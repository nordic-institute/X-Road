/*
 * The MIT License
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
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.common.mail.MailNotificationProperties;
import org.niis.xroad.globalconf.model.ApprovedCAInfo;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;
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
import org.springframework.stereotype.Service;

import javax.crypto.spec.SecretKeySpec;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
import java.util.Optional;
import java.util.function.Supplier;

import static ee.ria.xroad.common.util.CertUtils.createSelfSignedCertificate;
import static org.apache.commons.lang3.ArrayUtils.isEmpty;
import static org.niis.xroad.common.acme.AcmeCustomSchema.XRD_ACME;
import static org.niis.xroad.common.acme.AcmeCustomSchema.XRD_ACME_PROFILE_ID;
import static org.niis.xroad.common.acme.AcmeDeviationMessage.ACCOUNT_CREATION_FAILURE;
import static org.niis.xroad.common.acme.AcmeDeviationMessage.ACCOUNT_KEYSTORE_PASSWORD_MISSING;
import static org.niis.xroad.common.acme.AcmeDeviationMessage.ACCOUNT_KEY_PAIR_ERROR;
import static org.niis.xroad.common.acme.AcmeDeviationMessage.AUTHORIZATION_FAILURE;
import static org.niis.xroad.common.acme.AcmeDeviationMessage.AUTHORIZATION_WAIT_FAILURE;
import static org.niis.xroad.common.acme.AcmeDeviationMessage.CERTIFICATE_FAILURE;
import static org.niis.xroad.common.acme.AcmeDeviationMessage.CERTIFICATE_WAIT_FAILURE;
import static org.niis.xroad.common.acme.AcmeDeviationMessage.CHALLENGE_TRIGGER_FAILURE;
import static org.niis.xroad.common.acme.AcmeDeviationMessage.EAB_SECRET_LENGTH;
import static org.niis.xroad.common.acme.AcmeDeviationMessage.FETCHING_METADATA_ERROR;
import static org.niis.xroad.common.acme.AcmeDeviationMessage.FETCHING_RENEWAL_INFO_FAILURE;
import static org.niis.xroad.common.acme.AcmeDeviationMessage.HTTP_CHALLENGE_FILE_CREATION;
import static org.niis.xroad.common.acme.AcmeDeviationMessage.HTTP_CHALLENGE_FILE_DELETION;
import static org.niis.xroad.common.acme.AcmeDeviationMessage.HTTP_CHALLENGE_MISSING;
import static org.niis.xroad.common.acme.AcmeDeviationMessage.ORDER_CREATION_FAILURE;
import static org.niis.xroad.common.acme.AcmeDeviationMessage.ORDER_FINALIZATION_FAILURE;

@Slf4j
@Service
@RequiredArgsConstructor
public final class AcmeService {

    public static final int ORDER_NOT_AFTER_DAYS = 365;

    private final AcmeProperties acmeProperties;
    private final MailNotificationProperties mailNotificationProperties;
    private final AcmeConfig acmeConfig;

    public boolean isExternalAccountBindingRequired(String acmeServerDirectoryUrl) {
        Session session = new Session(acmeServerDirectoryUrl);
        return getMetadata(session).isExternalAccountRequired();
    }

    public List<X509Certificate> orderCertificateFromACMEServer(String commonName,
                                                                String subjectAltName,
                                                                KeyUsageInfo keyUsage,
                                                                ApprovedCAInfo caInfo,
                                                                String memberId,
                                                                byte[] certRequest) {
        KeyPair keyPair;
        try {
            keyPair = getAccountKeyPair(memberId, keyUsage, caInfo);
        } catch (GeneralSecurityException | OperatorCreationException | IOException e) {
            throw new AcmeServiceException(e, ACCOUNT_KEY_PAIR_ERROR.build());
        }

        Account account;
        try {
            account = startSession(keyUsage, caInfo, keyPair, memberId);
        } catch (AcmeException e) {
            throw new AcmeServiceException(e, ACCOUNT_CREATION_FAILURE.build());
        }

        Order order;
        try {
            order = createOrder(commonName, subjectAltName, account);
        } catch (AcmeException e) {
            throw new AcmeServiceException(e, ORDER_CREATION_FAILURE.build());
        }

        try {
            doAuthorizationAndFinalizeOrder(certRequest, order);
        } catch (AcmeException e) {
            throw new AcmeServiceException(e, ORDER_FINALIZATION_FAILURE.build());
        }

        Certificate cert = getCertificate(order);

        return cert != null ? cert.getCertificateChain() : null;

    }

    public void checkAccountKeyPairAndRenewIfNecessary(String memberId, ApprovedCAInfo caInfo, KeyUsageInfo keyUsage) {
        try {
            Login login = getLogin(memberId, caInfo, keyUsage);
            File acmeKeystoreFile = AcmeConfig.ACME_ACCOUNT_KEYSTORE_PATH.toFile();
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

    private KeyPair getAccountKeyPair(String memberId, KeyUsageInfo keyUsage, ApprovedCAInfo caInfo)
            throws GeneralSecurityException, IOException, OperatorCreationException {
        String alias = getAlias(memberId, keyUsage, caInfo);
        File acmeKeystoreFile = AcmeConfig.ACME_ACCOUNT_KEYSTORE_PATH.toFile();
        KeyStore keyStore;
        char[] storePassword = acmeProperties.getAccountKeystorePassword();
        if (isEmpty(storePassword)) {
            throw new AcmeServiceException(ACCOUNT_KEYSTORE_PASSWORD_MISSING.build());
        }
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
    }

    private String getAlias(String memberId, KeyUsageInfo keyUsage, ApprovedCAInfo caInfo) {
        AcmeProperties.Credentials credential = acmeProperties.getEabCredentials(caInfo.getName(), memberId);
        String alias = memberId;
        if (credential.getAuthKid() != null && keyUsage == KeyUsageInfo.AUTHENTICATION) {
            alias = "auth_" + alias;
        } else if (credential.getSignKid() != null && keyUsage == KeyUsageInfo.SIGNING) {
            alias = "sign_" + alias;
        }
        return alias;
    }

    private Account startSession(KeyUsageInfo keyUsage, ApprovedCAInfo caInfo, KeyPair keyPair, String memberId) throws AcmeException {
        log.info("Creating session with directory url: {}", caInfo.getAcmeServerDirectoryUrl());
        String acmeUri;
        if (caInfo.getAuthenticationCertificateProfileId() != null) {
            acmeUri = caInfo.getAcmeServerDirectoryUrl().replaceFirst("http", XRD_ACME_PROFILE_ID.getSchema());
        } else {
            acmeUri = caInfo.getAcmeServerDirectoryUrl().replaceFirst("http", XRD_ACME.getSchema());
        }
        Session session = new Session(acmeUri);
        Metadata metadata = getMetadata(session);
        log.debug("ACME server metadata: {}", metadata.getJSON().toString());
        log.debug("Creating account");
        AccountBuilder accountBuilder = new AccountBuilder()
                .agreeToTermsOfService()
                .useKeyPair(keyPair);
        Optional.ofNullable(mailNotificationProperties.getContacts())
                .map(contacts -> contacts.get(memberId))
                .ifPresent(accountBuilder::addContact);
        if (metadata.isExternalAccountRequired()) {
            accountWithEabCredentials(accountBuilder, keyUsage, caInfo, memberId);
        }
        return accountBuilder.create(session);
    }

    private void accountWithEabCredentials(AccountBuilder accountBuilder, KeyUsageInfo keyUsage, ApprovedCAInfo caInfo, String memberId) {
        AcmeProperties.Credentials credential = acmeProperties.getEabCredentials(caInfo.getName(), memberId);
        String kid, secret;
        if (credential.getAuthKid() != null && keyUsage == KeyUsageInfo.AUTHENTICATION) {
            kid = credential.getAuthKid();
            secret = credential.getAuthMacKey();
        } else if (credential.getSignKid() != null && keyUsage == KeyUsageInfo.SIGNING) {
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
            throw new AcmeServiceException(e, FETCHING_METADATA_ERROR.build());
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
            throw new BadRequestException(EAB_SECRET_LENGTH.build());
        }
    }

    private Order createOrder(String commonName, String subjectAltName, Account account) throws AcmeException {
        log.debug("Creating new order");
        return account.newOrder()
                .domains(subjectAltName != null ? subjectAltName : commonName)
                .notAfter(Instant.now().plus(Period.ofDays(ORDER_NOT_AFTER_DAYS)))
                .create();
    }

    private void doAuthorizationAndFinalizeOrder(byte[] certRequest, Order order)
            throws AcmeException {
        log.debug("Starting authorization");
        for (Authorization auth : order.getAuthorizations()) {
            if (auth.getStatus() == Status.PENDING) {
                Http01Challenge httpChallenge = auth.findChallenge(Http01Challenge.class)
                        .orElseThrow(() -> new AcmeServiceException(HTTP_CHALLENGE_MISSING.build()));
                String token = httpChallenge.getToken();
                String content = httpChallenge.getAuthorization();
                var acmeChallenge = AcmeConfig.ACME_CHALLENGE_PATH.resolve(token);
                try {
                    AtomicSave.execute(acmeChallenge, "tmp_challenge",
                            out -> out.write(content.getBytes(StandardCharsets.UTF_8)));
                } catch (Exception e) {
                    throw new AcmeServiceException(e, HTTP_CHALLENGE_FILE_CREATION.build());
                }
                try {
                    httpChallenge.trigger();
                } catch (AcmeException e) {
                    throw new AcmeServiceException(e, CHALLENGE_TRIGGER_FAILURE.build());
                }
                waitForTheChallengeToBeCompleted(httpChallenge);
                try {
                    Files.delete(acmeChallenge);
                } catch (IOException e) {
                    throw new AcmeServiceException(e, HTTP_CHALLENGE_FILE_DELETION.build());
                }
                log.debug("Finalizing order");
                order.execute(certRequest);
            }
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
                AUTHORIZATION_FAILURE,
                AUTHORIZATION_WAIT_FAILURE);
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
        waitForTheAcmeResourceToBeCompleted(order, order::getStatus, attempts, interval, CERTIFICATE_FAILURE, CERTIFICATE_WAIT_FAILURE);
        return order.getCertificate();
    }

    private Login getLogin(String memberId, ApprovedCAInfo approvedCA, KeyUsageInfo keyUsage) {
        KeyPair accountKeyPair;
        try {
            accountKeyPair = getAccountKeyPair(memberId, keyUsage, approvedCA);
        } catch (GeneralSecurityException | IOException | OperatorCreationException e) {
            throw new AcmeServiceException(e, ACCOUNT_KEY_PAIR_ERROR.build());
        }

        Session session = new Session(approvedCA.getAcmeServerDirectoryUrl());
        try {
            AccountBuilder accountBuilder = new AccountBuilder()
                    .useKeyPair(accountKeyPair);
            Optional.ofNullable(mailNotificationProperties.getContacts())
                    .map(contacts -> contacts.get(memberId))
                    .ifPresent(accountBuilder::addContact);
            accountWithEabCredentials(accountBuilder, keyUsage, approvedCA, memberId);
            return accountBuilder.createLogin(session);
        } catch (AcmeException e) {
            throw new AcmeServiceException(e, ACCOUNT_CREATION_FAILURE.build());
        }
    }

    public RenewalInfo getRenewalInfo(String memberId, ApprovedCAInfo approvedCA, X509Certificate certificate, KeyUsageInfo keyUsage) {
        Login login = getLogin(memberId, approvedCA, keyUsage);
        RenewalInfo renewalInfo;
        try {
            renewalInfo = login.bindRenewalInfo(certificate);
            renewalInfo.fetch();
        } catch (AcmeException e) {
            throw new AcmeServiceException(e, FETCHING_RENEWAL_INFO_FAILURE.build());
        }
        return renewalInfo;
    }

    public boolean isRenewalRequired(String memberId, ApprovedCAInfo approvedCA, X509Certificate certificate, KeyUsageInfo keyUsage) {
        return !getRenewalInfo(memberId, approvedCA, certificate, keyUsage).renewalIsNotRequired(Instant.now());
    }

    public Instant getSuggestedRenewalStartTime(String memberId,
                                                ApprovedCAInfo approvedCA,
                                                X509Certificate certificate,
                                                KeyUsageInfo keyUsage) {
        return getRenewalInfo(memberId, approvedCA, certificate, keyUsage).getSuggestedWindowStart();
    }

    public Instant getNextRenewalTime(String memberId, ApprovedCAInfo approvedCA, X509Certificate x509Certificate, KeyUsageInfo keyUsage) {
        try {
            if (hasRenewalInfo(memberId, approvedCA, keyUsage)) {
                return getSuggestedRenewalStartTime(memberId, approvedCA, x509Certificate, keyUsage);
            }
        } catch (Exception ex) {
            log.error(
                    "Retrieving renewal information from ACME Server failed. "
                            + "Falling back to fixed renewal time based on certificate expiration date: {}", ex.getMessage());
        }
        int renewalTimeBeforeExpirationDate = acmeConfig.getAcmeRenewalTimeBeforeExpirationDate();
        return x509Certificate.getNotAfter().toInstant().minus(renewalTimeBeforeExpirationDate, ChronoUnit.DAYS);
    }

    public boolean hasRenewalInfo(String memberId, ApprovedCAInfo approvedCA, KeyUsageInfo keyUsage) {
        try {
            return getLogin(memberId, approvedCA, keyUsage).getSession().resourceUrlOptional(Resource.RENEWAL_INFO).isPresent();
        } catch (AcmeException e) {
            throw new AcmeServiceException(e, FETCHING_RENEWAL_INFO_FAILURE.build());
        }
    }

    public List<X509Certificate> renew(String memberId, String subjectAltName, ApprovedCAInfo approvedCA,
                                       KeyUsageInfo keyUsage,
                                       X509Certificate oldCertificate, byte[] newCsr) {
        Login login = getLogin(memberId, approvedCA, keyUsage);
        Order order;
        try {
            order = login.newOrder()
                    .domains(subjectAltName)
                    .notAfter(Instant.now().plus(Period.ofDays(ORDER_NOT_AFTER_DAYS)))
                    .replaces(oldCertificate)
                    .create();
        } catch (AcmeException e) {
            throw new AcmeServiceException(e, ORDER_CREATION_FAILURE.build());
        }

        try {
            doAuthorizationAndFinalizeOrder(newCsr, order);
        } catch (AcmeException e) {
            throw new AcmeServiceException(e, ORDER_FINALIZATION_FAILURE.build());
        }

        Certificate cert = getCertificate(order);

        return cert != null ? cert.getCertificateChain() : null;
    }
}
