/*
 * The MIT License
 *
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.common.acme;

import ee.ria.xroad.common.util.AtomicSave;

import lombok.extern.slf4j.Slf4j;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.niis.xroad.common.exception.BadRequestException;
import org.shredzone.acme4j.Account;
import org.shredzone.acme4j.AccountBuilder;
import org.shredzone.acme4j.AcmeJsonResource;
import org.shredzone.acme4j.Authorization;
import org.shredzone.acme4j.Certificate;
import org.shredzone.acme4j.Login;
import org.shredzone.acme4j.Metadata;
import org.shredzone.acme4j.Order;
import org.shredzone.acme4j.RenewalInfo;
import org.shredzone.acme4j.Session;
import org.shredzone.acme4j.Status;
import org.shredzone.acme4j.challenge.Challenge;
import org.shredzone.acme4j.challenge.Http01Challenge;
import org.shredzone.acme4j.connector.Resource;
import org.shredzone.acme4j.exception.AcmeException;

import javax.crypto.spec.SecretKeySpec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Supplier;

/**
 * The acme4j machinery behind ACME certificate enrollment and renewal: account/session handling, order placement,
 * External Account Binding, HTTP-01 challenge fulfilment and ARI-aware renewal decisions.
 * <p>
 * Every operation is keyed on plain certificate-request parameters - directory URL, key pair, optional EAB
 * credentials, optional certificate profile id - never on a member identifier or a signer key-usage type. Callers
 * that need those concepts (e.g. the Security Server's member-cert ACME flows) resolve them on their own side and
 * translate them into an {@link AcmeAccountContext} before calling in here.
 */
@Slf4j
public final class AcmeClient {

    public static final int ORDER_NOT_AFTER_DAYS = 365;

    /**
     * Checks whether the CA at {@code directoryUrl} requires External Account Binding for new accounts.
     */
    public boolean isExternalAccountBindingRequired(String directoryUrl) {
        Session session = new Session(directoryUrl);
        return getMetadata(session).isExternalAccountRequired();
    }

    /**
     * Registers (or resumes) an ACME account and places a new certificate order, fulfilling HTTP-01 challenges
     * along the way.
     *
     * @return the issued certificate chain, or {@code null} if the CA did not return one
     */
    public List<X509Certificate> orderCertificate(AcmeAccountContext account, String commonName, String subjectAltName,
                                                  byte[] certificateRequest, AcmeChallengeSettings challengeSettings) {
        Account acmeAccount = createAccount(account);
        Order order = createOrder(commonName, subjectAltName, acmeAccount);
        doAuthorizationAndFinalizeOrder(account.certificateProfileId(), certificateRequest, order, challengeSettings);
        return certificateChainOf(getCertificate(order, challengeSettings));
    }

    /**
     * Places a renewal order for an existing account, replacing {@code certificateToReplace}, and fulfils HTTP-01
     * challenges along the way.
     *
     * @return the issued certificate chain, or {@code null} if the CA did not return one
     */
    public List<X509Certificate> renewCertificate(AcmeAccountContext account, String subjectAltName,
                                                  X509Certificate certificateToReplace, byte[] certificateRequest,
                                                  AcmeChallengeSettings challengeSettings) {
        Login login = login(account);
        Order order;
        try {
            order = login.newOrder()
                    .domains(subjectAltName)
                    .notAfter(Instant.now().plus(Period.ofDays(ORDER_NOT_AFTER_DAYS)))
                    .replaces(certificateToReplace)
                    .create();
        } catch (AcmeException e) {
            throw new AcmeServiceException(e, AcmeDeviationMessage.ORDER_CREATION_FAILURE.build());
        }
        doAuthorizationAndFinalizeOrder(account.certificateProfileId(), certificateRequest, order, challengeSettings);
        return certificateChainOf(getCertificate(order, challengeSettings));
    }

    /**
     * Tells the CA to replace the account's key with {@code newAccountKeyPair}. Persisting the new key pair locally
     * is the caller's responsibility.
     */
    public void changeAccountKey(AcmeAccountContext account, KeyPair newAccountKeyPair) {
        try {
            login(account).getAccount().changeKey(newAccountKeyPair);
        } catch (AcmeException e) {
            throw new AcmeServiceException(e, AcmeDeviationMessage.ACCOUNT_KEY_CHANGE_FAILURE.build());
        }
    }

    /**
     * Checks whether the CA offers ACME Renewal Information (ARI) for this account.
     */
    public boolean hasRenewalInfo(AcmeAccountContext account) {
        try {
            return login(account).getSession().resourceUrlOptional(Resource.RENEWAL_INFO).isPresent();
        } catch (AcmeException e) {
            throw new AcmeServiceException(e, AcmeDeviationMessage.FETCHING_RENEWAL_INFO_FAILURE.build());
        }
    }

    /**
     * Checks the CA's ACME Renewal Information (ARI) to decide whether {@code certificate} should be renewed now.
     */
    public boolean isRenewalRequired(AcmeAccountContext account, X509Certificate certificate) {
        return !getRenewalInfo(account, certificate).renewalIsNotRequired(Instant.now());
    }

    /**
     * Returns the start of the CA-suggested renewal window (ACME Renewal Information) for {@code certificate}.
     */
    public Instant getSuggestedRenewalStartTime(AcmeAccountContext account, X509Certificate certificate) {
        return getRenewalInfo(account, certificate).getSuggestedWindowStart();
    }

    /**
     * Returns when {@code certificate} should be renewed: the CA-suggested ACME Renewal Information window start
     * if the CA offers one, otherwise {@code fallbackDaysBeforeExpiration} days before the certificate expires.
     */
    public Instant getNextRenewalTime(AcmeAccountContext account, X509Certificate certificate, int fallbackDaysBeforeExpiration) {
        try {
            if (hasRenewalInfo(account)) {
                return getSuggestedRenewalStartTime(account, certificate);
            }
        } catch (Exception ex) {
            log.error(
                    "Retrieving renewal information from ACME Server failed. "
                            + "Falling back to fixed renewal time based on certificate expiration date: {}", ex.getMessage());
        }
        return certificate.getNotAfter().toInstant().minus(fallbackDaysBeforeExpiration, ChronoUnit.DAYS);
    }

    private static List<X509Certificate> certificateChainOf(Certificate cert) {
        return cert != null ? cert.getCertificateChain() : null;
    }

    private RenewalInfo getRenewalInfo(AcmeAccountContext account, X509Certificate certificate) {
        Login login = login(account);
        RenewalInfo renewalInfo;
        try {
            renewalInfo = login.bindRenewalInfo(certificate);
            renewalInfo.fetch();
        } catch (AcmeException e) {
            throw new AcmeServiceException(e, AcmeDeviationMessage.FETCHING_RENEWAL_INFO_FAILURE.build());
        }
        return renewalInfo;
    }

    /**
     * Logs into an existing account, exactly as it was registered by {@link #createAccount(AcmeAccountContext)}.
     * Unlike account creation, this never routes through the certificate-profile-id-aware custom scheme: renewal
     * and ARI calls carry no per-request profile id.
     */
    private Login login(AcmeAccountContext account) {
        try {
            Session session = new Session(account.directoryUrl());
            AccountBuilder accountBuilder = new AccountBuilder().useKeyPair(account.accountKeyPair());
            if (account.contactUri() != null) {
                accountBuilder.addContact(account.contactUri());
            }
            if (account.eabCredentials() != null) {
                applyEabCredentials(accountBuilder, account.eabCredentials().get());
            }
            return accountBuilder.createLogin(session);
        } catch (AcmeException e) {
            throw new AcmeServiceException(e, AcmeDeviationMessage.ACCOUNT_CREATION_FAILURE.build());
        }
    }

    private Account createAccount(AcmeAccountContext account) {
        try {
            log.info("Creating session with directory url: {}", account.directoryUrl());
            Session session = newAccountSession(account);
            Metadata metadata = getMetadata(session);
            log.debug("ACME server metadata: {}", metadata.getJSON());
            log.debug("Creating account");
            AccountBuilder accountBuilder = new AccountBuilder()
                    .agreeToTermsOfService()
                    .useKeyPair(account.accountKeyPair());
            if (account.contactUri() != null) {
                accountBuilder.addContact(account.contactUri());
            }
            if (metadata.isExternalAccountRequired() && account.eabCredentials() != null) {
                applyEabCredentials(accountBuilder, account.eabCredentials().get());
            }
            return accountBuilder.create(session);
        } catch (AcmeException e) {
            throw new AcmeServiceException(e, AcmeDeviationMessage.ACCOUNT_CREATION_FAILURE.build());
        }
    }

    private static Session newAccountSession(AcmeAccountContext account) {
        String scheme = account.certificateProfileId() != null
                ? AcmeCustomSchema.XRD_ACME_PROFILE_ID.getSchema()
                : AcmeCustomSchema.XRD_ACME.getSchema();
        return new Session(account.directoryUrl().replaceFirst("http", scheme));
    }

    private static void applyEabCredentials(AccountBuilder accountBuilder, AcmeEabCredentials eab) {
        if (eab.macKeyBase64Encoded()) {
            accountBuilder.withKeyIdentifier(eab.keyIdentifier(), padBase64(eab.macKey()));
        } else {
            accountBuilder.withKeyIdentifier(eab.keyIdentifier(),
                    new SecretKeySpec(eab.macKey().getBytes(StandardCharsets.UTF_8), "HMAC"));
        }
        accountBuilder.withMacAlgorithm(AlgorithmIdentifiers.HMAC_SHA256);
    }

    private static Metadata getMetadata(Session session) {
        try {
            return session.getMetadata();
        } catch (Exception e) {
            throw new AcmeServiceException(e, AcmeDeviationMessage.FETCHING_METADATA_ERROR.build());
        }
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private static String padBase64(String base64StringWithoutPadding) {
        if (base64StringWithoutPadding.length() % 4 == 3) {
            return base64StringWithoutPadding + "=";
        } else if (base64StringWithoutPadding.length() % 4 == 2) {
            return base64StringWithoutPadding + "==";
        } else if (base64StringWithoutPadding.length() % 4 == 0) {
            return base64StringWithoutPadding;
        } else {
            throw new BadRequestException(AcmeDeviationMessage.EAB_SECRET_LENGTH.build());
        }
    }

    private static Order createOrder(String commonName, String subjectAltName, Account account) {
        try {
            log.debug("Creating new order");
            return account.newOrder()
                    .domains(subjectAltName != null ? subjectAltName : commonName)
                    .notAfter(Instant.now().plus(Period.ofDays(ORDER_NOT_AFTER_DAYS)))
                    .create();
        } catch (AcmeException e) {
            throw new AcmeServiceException(e, AcmeDeviationMessage.ORDER_CREATION_FAILURE.build());
        }
    }

    private void doAuthorizationAndFinalizeOrder(String certificateProfileId, byte[] certRequest, Order order,
                                                 AcmeChallengeSettings challengeSettings) {
        log.debug("Starting authorization");
        for (Authorization auth : order.getAuthorizations()) {
            if (auth.getStatus() == Status.PENDING) {
                authorizeAndFinalizeOrder(certificateProfileId, auth, certRequest, order, challengeSettings);
            }
        }
    }

    private void authorizeAndFinalizeOrder(String certificateProfileId, Authorization auth, byte[] certRequest, Order order,
                                           AcmeChallengeSettings challengeSettings) {
        Http01Challenge httpChallenge = auth.findChallenge(Http01Challenge.class)
                .orElseThrow(() -> new AcmeServiceException(AcmeDeviationMessage.HTTP_CHALLENGE_MISSING.build()));
        String token = httpChallenge.getToken();
        Path challengeFile = AcmeHttp01Support.resolveChallengeFile(challengeSettings.challengeDirectory(), token);
        writeChallengeFile(challengeFile, httpChallenge.getAuthorization());
        triggerChallenge(httpChallenge);
        waitForTheChallengeToBeCompleted(httpChallenge, challengeSettings);
        deleteChallengeFile(challengeFile);
        finalizeOrder(certificateProfileId, order, certRequest);
    }

    private void writeChallengeFile(Path challengeFile, String content) {
        try {
            AtomicSave.execute(challengeFile, "tmp_challenge",
                    out -> out.write(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new AcmeServiceException(e, AcmeDeviationMessage.HTTP_CHALLENGE_FILE_CREATION.build());
        }
    }

    private void triggerChallenge(Http01Challenge httpChallenge) {
        try {
            httpChallenge.trigger();
        } catch (AcmeException e) {
            throw new AcmeServiceException(e, AcmeDeviationMessage.CHALLENGE_TRIGGER_FAILURE.build());
        }
    }

    private void deleteChallengeFile(Path challengeFile) {
        try {
            Files.delete(challengeFile);
        } catch (IOException e) {
            throw new AcmeServiceException(e, AcmeDeviationMessage.HTTP_CHALLENGE_FILE_DELETION.build());
        }
    }

    private void finalizeOrder(String certificateProfileId, Order order, byte[] certRequest) {
        log.debug("Finalizing order");
        try {
            AcmeProfileIdContext.runWithProfileId(certificateProfileId, () -> {
                order.execute(certRequest);
                return null;
            });
        } catch (AcmeException e) {
            throw new AcmeServiceException(e, AcmeDeviationMessage.ORDER_FINALIZATION_FAILURE.build());
        }
    }

    private void waitForTheChallengeToBeCompleted(Challenge challenge, AcmeChallengeSettings challengeSettings) {
        log.debug("Waiting for challenge to be completed");
        waitForTheAcmeResourceToBeCompleted(challenge,
                challenge::getStatus,
                challengeSettings.authorizationWaitAttempts(),
                challengeSettings.authorizationWaitIntervalSeconds(),
                AcmeDeviationMessage.AUTHORIZATION_FAILURE,
                AcmeDeviationMessage.AUTHORIZATION_WAIT_FAILURE);
    }

    private static void waitForTheAcmeResourceToBeCompleted(AcmeJsonResource acmeJsonResource,
                                                            Supplier<Status> statusSupplier,
                                                            int attempts,
                                                            long intervalSeconds,
                                                            AcmeDeviationMessage fetchFailure,
                                                            AcmeDeviationMessage fetchWaitFailure) {
        while (statusSupplier.get() != Status.VALID && attempts-- > 0) {
            if (statusSupplier.get() == Status.INVALID) {
                throw new AcmeServiceException(fetchFailure.build());
            }
            Instant now = Instant.now();
            try {
                Instant retryAfter = acmeJsonResource.fetch().orElse(now.plusSeconds(intervalSeconds));
                Thread.sleep(now.until(retryAfter, ChronoUnit.MILLIS));
            } catch (AcmeException e) {
                throw new AcmeServiceException(e, fetchFailure.build());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AcmeServiceException(e, fetchWaitFailure.build());
            }
        }
        if (statusSupplier.get() != Status.VALID) {
            throw new AcmeServiceException(fetchWaitFailure.build());
        }
    }

    private Certificate getCertificate(Order order, AcmeChallengeSettings challengeSettings) {
        log.debug("Getting the certificate");
        waitForTheAcmeResourceToBeCompleted(order, order::getStatus,
                challengeSettings.certificateWaitAttempts(), challengeSettings.certificateWaitIntervalSeconds(),
                AcmeDeviationMessage.CERTIFICATE_FAILURE, AcmeDeviationMessage.CERTIFICATE_WAIT_FAILURE);
        return order.getCertificate();
    }
}
