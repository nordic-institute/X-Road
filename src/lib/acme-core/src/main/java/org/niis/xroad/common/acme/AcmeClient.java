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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.niis.xroad.common.acme.config.AcmeConfig;
import org.niis.xroad.common.acme.config.AcmeProperties;
import org.niis.xroad.common.acme.provider.AcmeCustomSchema;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.common.vault.AcmeAccountKey;
import org.niis.xroad.common.vault.VaultClient;
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
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The acme4j machinery behind ACME certificate enrollment and renewal: account/session handling, order placement,
 * External Account Binding, HTTP-01 challenge fulfilment and ARI-aware renewal decisions.
 * <p>
 * Every operation is keyed on an {@link AcmeAccountContext} — plain account/CA facts — never on a member
 * identifier, a signer key-usage type, or any globalconf CA-info type. Callers that resolve those concepts (e.g.
 * {@link AcmeService} for member auth/sign certs, or DS TLS's own service) build the context on their own side.
 */
@Slf4j
@RequiredArgsConstructor
public final class AcmeClient {

    public static final int ORDER_NOT_AFTER_DAYS = 365;

    private final AcmeProperties acmeProperties;
    private final AcmeConfig acmeConfig;
    private final VaultClient vaultClient;

    public boolean isExternalAccountBindingRequired(String acmeServerDirectoryUrl) {
        Session session = new Session(acmeServerDirectoryUrl);
        return getMetadata(session).isExternalAccountRequired();
    }

    public List<X509Certificate> orderCertificate(String commonName, String subjectAltName, AcmeAccountContext account,
                                                  byte[] certRequest) {
        KeyPair keyPair = getAccountKeyPair(account);
        Account acmeAccount = startSession(account, keyPair);
        Order order = createOrder(commonName, subjectAltName, acmeAccount);
        doAuthorizationAndFinalizeOrder(account.certificateProfileId(), certRequest, order);

        Certificate cert = getCertificate(order);

        return cert != null ? cert.getCertificateChain() : null;
    }

    public void checkAccountKeyPairAndRenewIfNecessary(AcmeAccountContext account) {
        try {
            String alias = getAlias(account);
            Optional<AcmeAccountKey> accountKey = vaultClient.getAcmeAccountKey(alias);
            if (accountKey.isPresent() && isRenewalDue(accountKey.get())) {
                Login login = getLogin(account);
                KeyPair keyPair = generateAccountKeyPair();
                login.getAccount().changeKey(keyPair);
                vaultClient.createAcmeAccountKey(alias, newAccountKey(keyPair));
                log.info("Renewed acme account keypair for {}", account.accountAlias());
            }
        } catch (Exception e) {
            log.error("Renewing account key pair failed", e);
        }
    }

    private boolean isRenewalDue(AcmeAccountKey accountKey) {
        int renewalTimeBeforeExpirationDate = acmeConfig.getAcmeKeypairRenewalTimeBeforeExpirationDate();
        return Instant.now().isAfter(accountKey.expiresAt().minus(renewalTimeBeforeExpirationDate, ChronoUnit.DAYS));
    }

    private KeyPair getAccountKeyPair(AcmeAccountContext account) {
        String alias = getAlias(account);
        return vaultClient.getAcmeAccountKey(alias)
                .map(accountKey -> new KeyPair(accountKey.publicKey(), accountKey.privateKey()))
                .orElseGet(() -> {
                    log.debug("Creating keypair for {}", alias);
                    KeyPair keyPair = generateAccountKeyPair();
                    vaultClient.createAcmeAccountKey(alias, newAccountKey(keyPair));
                    return keyPair;
                });
    }

    private KeyPair generateAccountKeyPair() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(acmeConfig.getAcmeKeyLength(), new SecureRandom());
            return keyPairGenerator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new AcmeServiceException(e, AcmeDeviationMessage.ACCOUNT_KEY_PAIR_ERROR.build());
        }
    }

    private AcmeAccountKey newAccountKey(KeyPair keyPair) {
        Instant expiresAt = Instant.now().plus(acmeConfig.getAcmeCertificateAccountKeyPairExpiration(), ChronoUnit.DAYS);
        return new AcmeAccountKey(keyPair.getPrivate(), keyPair.getPublic(), expiresAt);
    }

    private String getAlias(AcmeAccountContext account) {
        AcmeProperties.Credentials credential = acmeProperties.getEabCredentials(account.caName(), account.accountAlias());
        String alias = account.accountAlias();
        if (credential.getAuthKid() != null && account.keyUsage() == AcmeKeyPurpose.AUTHENTICATION) {
            alias = "auth_" + alias;
        } else if (credential.getSignKid() != null && account.keyUsage() == AcmeKeyPurpose.SIGNING) {
            alias = "sign_" + alias;
        }
        return alias;
    }

    private Account startSession(AcmeAccountContext account, KeyPair keyPair) {
        try {
            log.info("Creating session with directory url: {}", account.acmeServerDirectoryUrl());
            String scheme = account.certificateProfileId() != null
                    ? AcmeCustomSchema.XRD_ACME_PROFILE_ID.getSchema()
                    : AcmeCustomSchema.XRD_ACME.getSchema();
            String acmeUri = account.acmeServerDirectoryUrl().replaceFirst("http", scheme);
            Session session = new Session(acmeUri);
            Metadata metadata = getMetadata(session);
            log.debug("ACME server metadata: {}", metadata.getJSON().toString());
            log.debug("Creating account");
            AccountBuilder accountBuilder = new AccountBuilder()
                    .agreeToTermsOfService()
                    .useKeyPair(keyPair);
            addContacts(accountBuilder, account.contacts());
            if (metadata.isExternalAccountRequired()) {
                accountWithEabCredentials(accountBuilder, account);
            }
            return accountBuilder.create(session);
        } catch (AcmeException e) {
            throw new AcmeServiceException(e, AcmeDeviationMessage.ACCOUNT_CREATION_FAILURE.build());
        }
    }

    private static void addContacts(AccountBuilder accountBuilder, List<String> contacts) {
        if (contacts != null) {
            contacts.forEach(accountBuilder::addContact);
        }
    }

    private void accountWithEabCredentials(AccountBuilder accountBuilder, AcmeAccountContext account) {
        AcmeProperties.Credentials credential = acmeProperties.getEabCredentials(account.caName(), account.accountAlias());
        String kid, secret;
        if (credential.getAuthKid() != null && account.keyUsage() == AcmeKeyPurpose.AUTHENTICATION) {
            kid = credential.getAuthKid();
            secret = credential.getAuthMacKey();
        } else if (credential.getSignKid() != null && account.keyUsage() == AcmeKeyPurpose.SIGNING) {
            kid = credential.getSignKid();
            secret = credential.getSignMacKey();
        } else {
            kid = credential.getKid();
            secret = credential.getMacKey();
        }
        if (acmeProperties.isEabMacKeyBase64Encoded(account.caName())) {
            String secretWithPadding = padBase64(secret);
            accountBuilder.withKeyIdentifier(kid, secretWithPadding);
        } else {
            accountBuilder.withKeyIdentifier(kid, new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HMAC"));
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

    private Order createOrder(String commonName, String subjectAltName, Account account) {
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

    private void doAuthorizationAndFinalizeOrder(String certificateProfileId, byte[] certRequest, Order order) {
        log.debug("Starting authorization");
        for (Authorization auth : order.getAuthorizations()) {
            if (auth.getStatus() == Status.PENDING) {
                authorizeAndFinalizeOrder(certificateProfileId, auth, certRequest, order);
            }
        }
    }

    private void authorizeAndFinalizeOrder(String certificateProfileId, Authorization auth, byte[] certRequest, Order order) {
        Http01Challenge httpChallenge = auth.findChallenge(Http01Challenge.class)
                .orElseThrow(() -> new AcmeServiceException(AcmeDeviationMessage.HTTP_CHALLENGE_MISSING.build()));
        String token = httpChallenge.getToken();
        if (!AcmeConfig.isValidChallengeToken(token)) {
            throw new AcmeServiceException(AcmeDeviationMessage.HTTP_CHALLENGE_TOKEN_INVALID.build());
        }
        var acmeChallenge = Path.of(acmeConfig.getAcmeChallengePath()).resolve(token);
        writeChallengeFile(acmeChallenge, httpChallenge.getAuthorization());
        triggerChallenge(httpChallenge);
        waitForTheChallengeToBeCompleted(httpChallenge);
        deleteChallengeFile(acmeChallenge);
        finalizeOrder(certificateProfileId, order, certRequest);
    }

    private void writeChallengeFile(Path acmeChallenge, String content) {
        try {
            AtomicSave.execute(acmeChallenge, "tmp_challenge",
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

    private void deleteChallengeFile(Path acmeChallenge) {
        try {
            Files.delete(acmeChallenge);
        } catch (IOException e) {
            throw new AcmeServiceException(e, AcmeDeviationMessage.HTTP_CHALLENGE_FILE_DELETION.build());
        }
    }

    private void finalizeOrder(String certificateProfileId, Order order, byte[] certRequest) {
        try {
            log.debug("Finalizing order");
            AcmeProfileIdContext.runWithProfileId(certificateProfileId, () -> {
                order.execute(certRequest);
                return null;
            });
        } catch (AcmeException e) {
            throw new AcmeServiceException(e, AcmeDeviationMessage.ORDER_FINALIZATION_FAILURE.build());
        }
    }

    private void waitForTheChallengeToBeCompleted(Challenge challenge) {
        log.debug("Waiting for challenge to be completed");
        int attempts = acmeConfig.getAcmeAuthorizationWaitAttempts();
        long interval = acmeConfig.getAcmeAuthorizationWaitInterval();
        waitForTheAcmeResourceToBeCompleted(challenge,
                challenge::getStatus,
                attempts,
                interval,
                AcmeDeviationMessage.AUTHORIZATION_FAILURE,
                AcmeDeviationMessage.AUTHORIZATION_WAIT_FAILURE);
    }

    private static void waitForTheAcmeResourceToBeCompleted(AcmeJsonResource acmeJsonResource,
                                                            Supplier<Status> statusSupplier,
                                                            int attempts,
                                                            long interval,
                                                            AcmeDeviationMessage fetchFailure,
                                                            AcmeDeviationMessage fetchWaitFailure) {
        while (statusSupplier.get() != Status.VALID && attempts-- > 0) {
            if (statusSupplier.get() == Status.INVALID) {
                throw new AcmeServiceException(fetchFailure.build());
            }
            Instant now = Instant.now();
            try {
                Instant retryAfter = acmeJsonResource.fetch().orElse(now.plusSeconds(interval));
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

    private Certificate getCertificate(Order order) {
        log.debug("Getting the certificate");
        int attempts = acmeConfig.getAcmeCertificateWaitAttempts();
        long interval = acmeConfig.getAcmeCertificateWaitInterval();
        waitForTheAcmeResourceToBeCompleted(order, order::getStatus, attempts, interval,
                AcmeDeviationMessage.CERTIFICATE_FAILURE, AcmeDeviationMessage.CERTIFICATE_WAIT_FAILURE);
        return order.getCertificate();
    }

    private Login getLogin(AcmeAccountContext account) {
        KeyPair accountKeyPair = getAccountKeyPair(account);
        Session session = new Session(account.acmeServerDirectoryUrl());
        try {
            AccountBuilder accountBuilder = new AccountBuilder()
                    .useKeyPair(accountKeyPair);
            addContacts(accountBuilder, account.contacts());
            accountWithEabCredentials(accountBuilder, account);
            return accountBuilder.createLogin(session);
        } catch (AcmeException e) {
            throw new AcmeServiceException(e, AcmeDeviationMessage.ACCOUNT_CREATION_FAILURE.build());
        }
    }

    private RenewalInfo getRenewalInfo(AcmeAccountContext account, X509Certificate certificate) {
        return getRenewalInfo(getLogin(account), certificate);
    }

    private RenewalInfo getRenewalInfo(Login login, X509Certificate certificate) {
        RenewalInfo renewalInfo;
        try {
            renewalInfo = login.bindRenewalInfo(certificate);
            renewalInfo.fetch();
        } catch (AcmeException e) {
            throw new AcmeServiceException(e, AcmeDeviationMessage.FETCHING_RENEWAL_INFO_FAILURE.build());
        }
        return renewalInfo;
    }

    public boolean isRenewalRequired(AcmeAccountContext account, X509Certificate certificate) {
        return !getRenewalInfo(account, certificate).renewalIsNotRequired(Instant.now());
    }

    public Instant getNextRenewalTime(AcmeAccountContext account, X509Certificate x509Certificate) {
        return resolveNextRenewalTime(getLogin(account), x509Certificate);
    }

    private Instant resolveNextRenewalTime(Login login, X509Certificate x509Certificate) {
        try {
            if (hasRenewalInfo(login)) {
                return getRenewalInfo(login, x509Certificate).getSuggestedWindowStart();
            }
        } catch (Exception ex) {
            log.error(
                    "Retrieving renewal information from ACME Server failed. "
                            + "Falling back to fixed renewal time based on certificate expiration date: {}", ex.getMessage());
        }
        int renewalTimeBeforeExpirationDate = acmeConfig.getAcmeRenewalTimeBeforeExpirationDate();
        return x509Certificate.getNotAfter().toInstant().minus(renewalTimeBeforeExpirationDate, ChronoUnit.DAYS);
    }

    public boolean hasRenewalInfo(AcmeAccountContext account) {
        return hasRenewalInfo(getLogin(account));
    }

    private boolean hasRenewalInfo(Login login) {
        try {
            return login.getSession().resourceUrlOptional(Resource.RENEWAL_INFO).isPresent();
        } catch (AcmeException e) {
            throw new AcmeServiceException(e, AcmeDeviationMessage.FETCHING_RENEWAL_INFO_FAILURE.build());
        }
    }

    public List<X509Certificate> renew(AcmeAccountContext account, String subjectAltName,
                                       X509Certificate oldCertificate, byte[] newCsr) {
        Login login = getLogin(account);
        Order order;
        try {
            order = login.newOrder()
                    .domains(subjectAltName)
                    .notAfter(Instant.now().plus(Period.ofDays(ORDER_NOT_AFTER_DAYS)))
                    .replaces(oldCertificate)
                    .create();
        } catch (AcmeException e) {
            throw new AcmeServiceException(e, AcmeDeviationMessage.ORDER_CREATION_FAILURE.build());
        }
        doAuthorizationAndFinalizeOrder(account.certificateProfileId(), newCsr, order);

        Certificate cert = getCertificate(order);

        return cert != null ? cert.getCertificateChain() : null;
    }
}
