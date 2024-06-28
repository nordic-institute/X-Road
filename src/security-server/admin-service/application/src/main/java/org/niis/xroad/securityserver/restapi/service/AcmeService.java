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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.globalconf.ApprovedCAInfo;
import ee.ria.xroad.common.util.AtomicSave;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.operator.OperatorCreationException;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.niis.xroad.common.exception.ValidationFailureException;
import org.niis.xroad.securityserver.restapi.config.AcmeProperties;
import org.shredzone.acme4j.Account;
import org.shredzone.acme4j.AccountBuilder;
import org.shredzone.acme4j.AcmeJsonResource;
import org.shredzone.acme4j.Authorization;
import org.shredzone.acme4j.Certificate;
import org.shredzone.acme4j.Metadata;
import org.shredzone.acme4j.Order;
import org.shredzone.acme4j.Session;
import org.shredzone.acme4j.Status;
import org.shredzone.acme4j.challenge.Challenge;
import org.shredzone.acme4j.challenge.Http01Challenge;
import org.shredzone.acme4j.exception.AcmeException;
import org.springframework.stereotype.Service;

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
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static ee.ria.xroad.common.util.CertUtils.createSelfSignedCertificate;
import static org.niis.xroad.securityserver.restapi.service.AcmeCustomSchema.XRD_ACME;
import static org.niis.xroad.securityserver.restapi.service.AcmeCustomSchema.XRD_ACME_PROFILE_ID;
import static org.niis.xroad.securityserver.restapi.service.AcmeDeviationMessage.ACCOUNT_CREATION_FAILURE;
import static org.niis.xroad.securityserver.restapi.service.AcmeDeviationMessage.ACCOUNT_KEY_PAIR_ERROR;
import static org.niis.xroad.securityserver.restapi.service.AcmeDeviationMessage.AUTHORIZATION_FAILURE;
import static org.niis.xroad.securityserver.restapi.service.AcmeDeviationMessage.AUTHORIZATION_WAIT_FAILURE;
import static org.niis.xroad.securityserver.restapi.service.AcmeDeviationMessage.CERTIFICATE_FAILURE;
import static org.niis.xroad.securityserver.restapi.service.AcmeDeviationMessage.CERTIFICATE_WAIT_FAILURE;
import static org.niis.xroad.securityserver.restapi.service.AcmeDeviationMessage.CHALLENGE_TRIGGER_FAILURE;
import static org.niis.xroad.securityserver.restapi.service.AcmeDeviationMessage.EAB_SECRET_LENGTH;
import static org.niis.xroad.securityserver.restapi.service.AcmeDeviationMessage.FETCHING_METADATA_ERROR;
import static org.niis.xroad.securityserver.restapi.service.AcmeDeviationMessage.HTTP_CHALLENGE_FILE_CREATION;
import static org.niis.xroad.securityserver.restapi.service.AcmeDeviationMessage.HTTP_CHALLENGE_FILE_DELETION;
import static org.niis.xroad.securityserver.restapi.service.AcmeDeviationMessage.HTTP_CHALLENGE_MISSING;
import static org.niis.xroad.securityserver.restapi.service.AcmeDeviationMessage.ORDER_CREATION_FAILURE;
import static org.niis.xroad.securityserver.restapi.service.AcmeDeviationMessage.ORDER_FINALIZATION_FAILURE;

@Slf4j
@Service
@RequiredArgsConstructor
public final class AcmeService {

    @Setter
    private String acmeAccountKeystorePath = SystemProperties.getConfPath() + "ssl/acme.p12";
    private final String acmeChallengePath = SystemProperties.getConfPath() + "acme-challenge/";

    private final AcmeProperties acmeProperties;

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
            keyPair = getAccountKeyPair(memberId);
        } catch (GeneralSecurityException | OperatorCreationException | IOException e) {
            throw new AcmeServiceException(ACCOUNT_KEY_PAIR_ERROR, e);
        }

        Account account;
        try {
            account = startSession(keyUsage, caInfo, keyPair, memberId);
        } catch (AcmeException e) {
            throw new AcmeServiceException(ACCOUNT_CREATION_FAILURE, e);
        }

        Order order;
        try {
            order = createOrder(commonName, subjectAltName, account);
        } catch (AcmeException e) {
            throw new AcmeServiceException(ORDER_CREATION_FAILURE, e);
        }

        try {
            doAuthorizationAndFinalizeOrder(certRequest, order);
        } catch (AcmeException e) {
            throw new AcmeServiceException(ORDER_FINALIZATION_FAILURE, e);
        }

        Certificate cert = getCertificate(order);

        return cert != null ? cert.getCertificateChain() : null;

    }

    private KeyPair getAccountKeyPair(String memberId) throws GeneralSecurityException, IOException, OperatorCreationException {
        File acmeKeystoreFile = new File(acmeAccountKeystorePath);
        KeyStore keyStore;
        if (acmeKeystoreFile.exists()) {
            keyStore = CryptoUtils.loadPkcs12KeyStore(acmeKeystoreFile, null);
        } else {
            keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(null, null);
        }
        X509Certificate certificate = (X509Certificate) keyStore.getCertificate(memberId);
        KeyPair keyPair;
        if (certificate != null) {
            log.debug("Loading keypair");
            PublicKey publicKey = certificate.getPublicKey();
            PrivateKey privateKey = (PrivateKey) keyStore.getKey(memberId, memberId.toCharArray());
            keyPair = new KeyPair(publicKey, privateKey);
        } else {
            log.debug("Creating keypair");
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(SystemProperties.getSignerKeyLength(), new SecureRandom());
            keyPair = keyPairGenerator.generateKeyPair();

            X509Certificate[] certificateChain = createSelfSignedCertificate(memberId, keyPair);

            keyStore.setKeyEntry(
                    memberId,
                    keyPair.getPrivate(),
                    memberId.toCharArray(),
                    certificateChain);
            try (OutputStream outputStream = new FileOutputStream(acmeKeystoreFile)) {
                keyStore.store(outputStream, null);
                outputStream.flush();
            }
        }
        return keyPair;
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
        Optional.ofNullable(acmeProperties.getContacts())
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
            throw new AcmeServiceException(FETCHING_METADATA_ERROR, e);
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
            throw new ValidationFailureException(EAB_SECRET_LENGTH);
        }
    }

    private Order createOrder(String commonName, String subjectAltName, Account account) throws AcmeException {
        log.debug("Creating new order");
        return account.newOrder()
                .domains(subjectAltName != null ? subjectAltName : commonName)
                .notAfter(Instant.now().plus(Duration.ofDays(1L)))
                .create();
    }

    private void doAuthorizationAndFinalizeOrder(byte[] certRequest, Order order)
            throws AcmeException {
        log.debug("Starting authorization");
        for (Authorization auth : order.getAuthorizations()) {
            if (auth.getStatus() == Status.PENDING) {
                Http01Challenge httpChallenge = auth.findChallenge(Http01Challenge.class)
                        .orElseThrow(() -> new AcmeServiceException(HTTP_CHALLENGE_MISSING));
                String token = httpChallenge.getToken();
                String content = httpChallenge.getAuthorization();
                String acmeChallenge = acmeChallengePath + token;
                try {
                    AtomicSave.execute(acmeChallenge, "tmp_challenge",
                            out -> out.write(content.getBytes(StandardCharsets.UTF_8)));
                } catch (Exception e) {
                    throw new AcmeServiceException(HTTP_CHALLENGE_FILE_CREATION, e);
                }
                try {
                    httpChallenge.trigger();
                } catch (AcmeException e) {
                    throw new AcmeServiceException(CHALLENGE_TRIGGER_FAILURE, e);
                }
                waitForTheChallengeToBeCompleted(httpChallenge);
                try {
                    Files.delete(Path.of(acmeChallenge));
                } catch (IOException e) {
                    throw new AcmeServiceException(HTTP_CHALLENGE_FILE_DELETION, e);
                }
                log.debug("Finalizing order");
                order.execute(certRequest);
            }
        }
    }

    private static void waitForTheChallengeToBeCompleted(Challenge challenge) {
        log.debug("Waiting for challenge to be completed");
        int attempts = SystemProperties.getAcmeAuthorizationWaitAttempts();
        long interval = SystemProperties.getAcmeAuthorizationWaitInterval();
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
        while (statusSupplier.get() != Status.VALID  && attempts-- > 0) {
            if (statusSupplier.get() == Status.INVALID) {
                throw new AcmeServiceException(fetchFailure);
            }
            Instant now = Instant.now();
            try {
                Instant retryAfter = acmeJsonResource.fetch().orElse(now.plusSeconds(interval));
                Thread.sleep(now.until(retryAfter, ChronoUnit.MILLIS));
            } catch (AcmeException e) {
                throw new AcmeServiceException(fetchFailure, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AcmeServiceException(fetchWaitFailure, e);
            }
        }
        if (statusSupplier.get() != Status.VALID) {
            throw new AcmeServiceException(fetchWaitFailure);
        }
    }

    private Certificate getCertificate(Order order) {
        log.debug("Getting the certificate");
        int attempts = SystemProperties.getAcmeCertificateWaitAttempts();
        long interval = SystemProperties.getAcmeCertificateWaitInterval();
        waitForTheAcmeResourceToBeCompleted(order, order::getStatus, attempts, interval, CERTIFICATE_FAILURE, CERTIFICATE_WAIT_FAILURE);
        return order.getCertificate();
    }
}
