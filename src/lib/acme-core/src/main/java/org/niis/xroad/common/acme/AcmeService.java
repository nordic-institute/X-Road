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
import ee.ria.xroad.common.util.CryptoUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.operator.OperatorCreationException;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.niis.xroad.common.acme.config.AcmeConfig;
import org.niis.xroad.common.acme.config.AcmeProperties;
import org.niis.xroad.common.acme.provider.AcmeCustomSchema;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.globalconf.model.ApprovedCAInfo;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Supplier;

import static ee.ria.xroad.common.util.CertUtils.createSelfSignedCertificate;
import static org.apache.commons.lang3.ArrayUtils.isEmpty;

@Slf4j
@RequiredArgsConstructor
public final class AcmeService {

    public static final int ORDER_NOT_AFTER_DAYS = 365;

    private final AcmeProperties acmeProperties;
    private final AcmeConfig acmeConfig;
    private final AccountKeystorePasswordProvider accountKeystorePasswordProvider;

    public boolean isExternalAccountBindingRequired(String acmeServerDirectoryUrl) {
        Session session = new Session(acmeServerDirectoryUrl);
        return getMetadata(session).isExternalAccountRequired();
    }

    public List<X509Certificate> orderCertificateFromACMEServer(String commonName,
                                                                String subjectAltName,
                                                                AcmeKeyPurpose keyUsage,
                                                                ApprovedCAInfo caInfo,
                                                                String memberId,
                                                                byte[] certRequest,
                                                                List<String> contacts) {
        KeyPair keyPair = getAccountKeyPair(memberId, keyUsage, caInfo);
        Account account = startSession(keyUsage, caInfo, keyPair, memberId, contacts);
        Order order = createOrder(commonName, subjectAltName, account);
        doAuthorizationAndFinalizeOrder(certRequest, order);

        Certificate cert = getCertificate(order);

        return cert != null ? cert.getCertificateChain() : null;

    }

    public void checkAccountKeyPairAndRenewIfNecessary(String memberId, ApprovedCAInfo caInfo, AcmeKeyPurpose keyUsage,
                                                        List<String> contacts) {
        try {
            Login login = getLogin(memberId, caInfo, keyUsage, contacts);
            File acmeKeystoreFile = Path.of(acmeConfig.getAcmeAccountKeystorePath()).toFile();
            char[] storePassword = acmeProperties.getAccountKeystorePassword();
            KeyStore keyStore = CryptoUtils.loadPkcs12KeyStore(acmeKeystoreFile, storePassword);
            String alias = getAlias(memberId, keyUsage, caInfo);
            X509Certificate certificate = (X509Certificate) keyStore.getCertificate(alias);
            int renewalTimeBeforeExpirationDate = acmeConfig.getAcmeKeypairRenewalTimeBeforeExpirationDate();
            if (certificate != null && Instant.now()
                    .isAfter(certificate.getNotAfter().toInstant().minus(renewalTimeBeforeExpirationDate, ChronoUnit.DAYS))) {
                KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
                keyPairGenerator.initialize(acmeConfig.getAcmeKeyLength(), new SecureRandom());
                KeyPair keyPair = keyPairGenerator.generateKeyPair();
                login.getAccount().changeKey(keyPair);

                long expirationInDays = acmeConfig.getAcmeCertificateAccountKeyPairExpiration();
                X509Certificate[] certificateChain = createSelfSignedCertificate(alias, keyPair, expirationInDays);
                keyStore.setKeyEntry(
                        alias,
                        keyPair.getPrivate(),
                        alias.toCharArray(),
                        certificateChain);
                log.info("Renewed acme account keypair for {}", memberId);
            }
        } catch (Exception e) {
            log.error("Renewing account key pair failed", e);
        }
    }

    private KeyPair getAccountKeyPair(String memberId, AcmeKeyPurpose keyUsage, ApprovedCAInfo caInfo) {
        String alias = getAlias(memberId, keyUsage, caInfo);
        File acmeKeystoreFile = Path.of(acmeConfig.getAcmeAccountKeystorePath()).toFile();
        KeyStore keyStore;
        char[] storePassword = acmeProperties.getAccountKeystorePassword();
        if (isEmpty(storePassword)) {
            if (acmeKeystoreFile.exists()) {
                throw new AcmeServiceException(AcmeDeviationMessage.ACCOUNT_KEYSTORE_PASSWORD_MISSING.build());
            } else {
                storePassword = accountKeystorePasswordProvider.createNewPassword();
            }
        }
        try {
            if (acmeKeystoreFile.exists()) {
                keyStore = CryptoUtils.loadPkcs12KeyStore(acmeKeystoreFile, storePassword);
            } else {
                keyStore = KeyStore.getInstance("PKCS12");
                keyStore.load(null, storePassword);
            }
            X509Certificate certificate = (X509Certificate) keyStore.getCertificate(alias);
            KeyPair keyPair;
            if (certificate != null) {
                log.debug("Loading keypair");
                PublicKey publicKey = certificate.getPublicKey();
                PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, alias.toCharArray());
                keyPair = new KeyPair(publicKey, privateKey);
            } else {
                log.debug("Creating keypair");
                KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
                keyPairGenerator.initialize(acmeConfig.getAcmeKeyLength(), new SecureRandom());
                keyPair = keyPairGenerator.generateKeyPair();

                long expirationInDays = acmeConfig.getAcmeCertificateAccountKeyPairExpiration();
                X509Certificate[] certificateChain = createSelfSignedCertificate(alias, keyPair, expirationInDays);

                keyStore.setKeyEntry(
                        alias,
                        keyPair.getPrivate(),
                        alias.toCharArray(),
                        certificateChain);
                try (OutputStream outputStream = new FileOutputStream(acmeKeystoreFile)) {
                    keyStore.store(outputStream, storePassword);
                    outputStream.flush();
                }
            }
            return keyPair;
        } catch (GeneralSecurityException | OperatorCreationException | IOException e) {
            throw new AcmeServiceException(e, AcmeDeviationMessage.ACCOUNT_KEY_PAIR_ERROR.build());
        }
    }

    private String getAlias(String memberId, AcmeKeyPurpose keyUsage, ApprovedCAInfo caInfo) {
        AcmeProperties.Credentials credential = acmeProperties.getEabCredentials(caInfo.getName(), memberId);
        String alias = memberId;
        if (credential.getAuthKid() != null && keyUsage == AcmeKeyPurpose.AUTHENTICATION) {
            alias = "auth_" + alias;
        } else if (credential.getSignKid() != null && keyUsage == AcmeKeyPurpose.SIGNING) {
            alias = "sign_" + alias;
        }
        return alias;
    }

    private Account startSession(AcmeKeyPurpose keyUsage, ApprovedCAInfo caInfo, KeyPair keyPair, String memberId,
                                 List<String> contacts) {
        try {
            log.info("Creating session with directory url: {}", caInfo.getAcmeServerDirectoryUrl());
            String acmeUri;
            if (caInfo.getAuthenticationCertificateProfileId() != null) {
                acmeUri = caInfo.getAcmeServerDirectoryUrl().replaceFirst("http", AcmeCustomSchema.XRD_ACME_PROFILE_ID.getSchema());
            } else {
                acmeUri = caInfo.getAcmeServerDirectoryUrl().replaceFirst("http", AcmeCustomSchema.XRD_ACME.getSchema());
            }
            Session session = new Session(acmeUri);
            Metadata metadata = getMetadata(session);
            log.debug("ACME server metadata: {}", metadata.getJSON().toString());
            log.debug("Creating account");
            AccountBuilder accountBuilder = new AccountBuilder()
                    .agreeToTermsOfService()
                    .useKeyPair(keyPair);
            addContacts(accountBuilder, contacts);
            if (metadata.isExternalAccountRequired()) {
                accountWithEabCredentials(accountBuilder, keyUsage, caInfo, memberId);
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

    private void accountWithEabCredentials(AccountBuilder accountBuilder, AcmeKeyPurpose keyUsage, ApprovedCAInfo caInfo,
                                           String memberId) {
        AcmeProperties.Credentials credential = acmeProperties.getEabCredentials(caInfo.getName(), memberId);
        String kid, secret;
        if (credential.getAuthKid() != null && keyUsage == AcmeKeyPurpose.AUTHENTICATION) {
            kid = credential.getAuthKid();
            secret = credential.getAuthMacKey();
        } else if (credential.getSignKid() != null && keyUsage == AcmeKeyPurpose.SIGNING) {
            kid = credential.getSignKid();
            secret = credential.getSignMacKey();
        } else {
            kid = credential.getKid();
            secret = credential.getMacKey();
        }
        if (acmeProperties.isEabMacKeyBase64Encoded(caInfo.getName())) {
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

    private void doAuthorizationAndFinalizeOrder(byte[] certRequest, Order order) {
        log.debug("Starting authorization");
        for (Authorization auth : order.getAuthorizations()) {
            if (auth.getStatus() == Status.PENDING) {
                authorizeAndFinalizeOrder(auth, certRequest, order);
            }
        }
    }

    private void authorizeAndFinalizeOrder(Authorization auth, byte[] certRequest, Order order) {
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
        finalizeOrder(order, certRequest);
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

    private void finalizeOrder(Order order, byte[] certRequest) {
        try {
            log.debug("Finalizing order");
            order.execute(certRequest);
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

    private Login getLogin(String memberId, ApprovedCAInfo approvedCA, AcmeKeyPurpose keyUsage, List<String> contacts) {
        KeyPair accountKeyPair = getAccountKeyPair(memberId, keyUsage, approvedCA);
        Session session = new Session(approvedCA.getAcmeServerDirectoryUrl());
        try {
            AccountBuilder accountBuilder = new AccountBuilder()
                    .useKeyPair(accountKeyPair);
            addContacts(accountBuilder, contacts);
            accountWithEabCredentials(accountBuilder, keyUsage, approvedCA, memberId);
            return accountBuilder.createLogin(session);
        } catch (AcmeException e) {
            throw new AcmeServiceException(e, AcmeDeviationMessage.ACCOUNT_CREATION_FAILURE.build());
        }
    }

    public RenewalInfo getRenewalInfo(String memberId, ApprovedCAInfo approvedCA, X509Certificate certificate,
                                      AcmeKeyPurpose keyUsage, List<String> contacts) {
        Login login = getLogin(memberId, approvedCA, keyUsage, contacts);
        RenewalInfo renewalInfo;
        try {
            renewalInfo = login.bindRenewalInfo(certificate);
            renewalInfo.fetch();
        } catch (AcmeException e) {
            throw new AcmeServiceException(e, AcmeDeviationMessage.FETCHING_RENEWAL_INFO_FAILURE.build());
        }
        return renewalInfo;
    }

    public boolean isRenewalRequired(String memberId, ApprovedCAInfo approvedCA, X509Certificate certificate,
                                     AcmeKeyPurpose keyUsage, List<String> contacts) {
        return !getRenewalInfo(memberId, approvedCA, certificate, keyUsage, contacts).renewalIsNotRequired(Instant.now());
    }

    public Instant getSuggestedRenewalStartTime(String memberId,
                                                ApprovedCAInfo approvedCA,
                                                X509Certificate certificate,
                                                AcmeKeyPurpose keyUsage,
                                                List<String> contacts) {
        return getRenewalInfo(memberId, approvedCA, certificate, keyUsage, contacts).getSuggestedWindowStart();
    }

    public Instant getNextRenewalTime(String memberId, ApprovedCAInfo approvedCA, X509Certificate x509Certificate,
                                      AcmeKeyPurpose keyUsage, List<String> contacts) {
        try {
            if (hasRenewalInfo(memberId, approvedCA, keyUsage, contacts)) {
                return getSuggestedRenewalStartTime(memberId, approvedCA, x509Certificate, keyUsage, contacts);
            }
        } catch (Exception ex) {
            log.error(
                    "Retrieving renewal information from ACME Server failed. "
                            + "Falling back to fixed renewal time based on certificate expiration date: {}", ex.getMessage());
        }
        int renewalTimeBeforeExpirationDate = acmeConfig.getAcmeRenewalTimeBeforeExpirationDate();
        return x509Certificate.getNotAfter().toInstant().minus(renewalTimeBeforeExpirationDate, ChronoUnit.DAYS);
    }

    public boolean hasRenewalInfo(String memberId, ApprovedCAInfo approvedCA, AcmeKeyPurpose keyUsage, List<String> contacts) {
        try {
            return getLogin(memberId, approvedCA, keyUsage, contacts).getSession().resourceUrlOptional(Resource.RENEWAL_INFO).isPresent();
        } catch (AcmeException e) {
            throw new AcmeServiceException(e, AcmeDeviationMessage.FETCHING_RENEWAL_INFO_FAILURE.build());
        }
    }

    public List<X509Certificate> renew(String memberId, String subjectAltName, ApprovedCAInfo approvedCA,
                                       AcmeKeyPurpose keyUsage,
                                       X509Certificate oldCertificate, byte[] newCsr, List<String> contacts) {
        Login login = getLogin(memberId, approvedCA, keyUsage, contacts);
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
        doAuthorizationAndFinalizeOrder(newCsr, order);

        Certificate cert = getCertificate(order);

        return cert != null ? cert.getCertificateChain() : null;
    }
}
