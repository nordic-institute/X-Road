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
package org.niis.xroad.securityserver.restapi.util;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.globalconf.ApprovedCAInfo;
import ee.ria.xroad.common.util.AtomicSave;
import ee.ria.xroad.common.util.CryptoUtils;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.operator.OperatorCreationException;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.common.exception.ValidationFailureException;
import org.niis.xroad.securityserver.restapi.config.AcmeEabProperties;
import org.niis.xroad.securityserver.restapi.service.AcmeServiceException;
import org.shredzone.acme4j.Account;
import org.shredzone.acme4j.AccountBuilder;
import org.shredzone.acme4j.Authorization;
import org.shredzone.acme4j.Certificate;
import org.shredzone.acme4j.Metadata;
import org.shredzone.acme4j.Order;
import org.shredzone.acme4j.Session;
import org.shredzone.acme4j.Status;
import org.shredzone.acme4j.challenge.Http01Challenge;
import org.shredzone.acme4j.exception.AcmeException;
import org.shredzone.acme4j.exception.AcmeRetryAfterException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

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
import java.util.concurrent.TimeUnit;

import static ee.ria.xroad.common.util.CertUtils.createSelfSignedCertificate;
import static org.niis.xroad.securityserver.restapi.service.AcmeDeviationMessage.ACCOUNT_CREATION_FAILURE;
import static org.niis.xroad.securityserver.restapi.service.AcmeDeviationMessage.ACCOUNT_KEY_PAIR_ERROR;
import static org.niis.xroad.securityserver.restapi.service.AcmeDeviationMessage.AUTHORIZATION_FAILURE;
import static org.niis.xroad.securityserver.restapi.service.AcmeDeviationMessage.AUTHORIZATION_WAIT_FAILURE;
import static org.niis.xroad.securityserver.restapi.service.AcmeDeviationMessage.CERTIFICATE_FAILURE;
import static org.niis.xroad.securityserver.restapi.service.AcmeDeviationMessage.CERTIFICATE_WAIT_FAILURE;
import static org.niis.xroad.securityserver.restapi.service.AcmeDeviationMessage.CHALLENGE_TRIGGER_FAILURE;
import static org.niis.xroad.securityserver.restapi.service.AcmeDeviationMessage.EAB_CREDENTIALS_MISSING;
import static org.niis.xroad.securityserver.restapi.service.AcmeDeviationMessage.EAB_SECRET_LENGTH;
import static org.niis.xroad.securityserver.restapi.service.AcmeDeviationMessage.FETCHING_METADATA_ERROR;
import static org.niis.xroad.securityserver.restapi.service.AcmeDeviationMessage.HTTP_CHALLENGE_FILE_CREATION;
import static org.niis.xroad.securityserver.restapi.service.AcmeDeviationMessage.HTTP_CHALLENGE_FILE_DELETION;
import static org.niis.xroad.securityserver.restapi.service.AcmeDeviationMessage.HTTP_CHALLENGE_MISSING;
import static org.niis.xroad.securityserver.restapi.service.AcmeDeviationMessage.ORDER_CREATION_FAILURE;
import static org.niis.xroad.securityserver.restapi.service.AcmeDeviationMessage.ORDER_FINALIZATION_FAILURE;

@Slf4j
@EnableConfigurationProperties(AcmeEabProperties.class)
@Component
@RequiredArgsConstructor
public final class AcmeHelper {

    @Setter
    private String acmeAccountKeystorePath = SystemProperties.getConfPath() + "ssl/acme.p12";
    private final String acmeAccountKeystorePassword = "acme";
    private final String acmeChallengePath = SystemProperties.getConfPath() + "acme-challenge/";

    private final AcmeEabProperties acmeEabProperties;

    public boolean isExternalAccountBindingRequired(String acmeServerDirectoryUrl) {
        Session session = new Session(acmeServerDirectoryUrl);
        return getMetadata(session).isExternalAccountRequired();
    }

    public boolean hasExternalAccountBindingCredentials(String caName, String memberCode) {
        Optional<AcmeEabProperties.Credentials> credentials =
                Optional.ofNullable(acmeEabProperties.getEabCredentials())
                        .map(AcmeEabProperties.EabCredentials::getCertificateAuthorities)
                        .map(certAuthorities -> certAuthorities.get(caName))
                        .map(AcmeEabProperties.CA::getMembers)
                        .map(members -> members.get(memberCode));
        return credentials.isPresent();
    }


    public List<X509Certificate> orderCertificateFromACMEServer(String commonName,
                                                                       String subjectAltName,
                                                                       ApprovedCAInfo caInfo,
                                                                       String memberCode,
                                                                       byte[] certRequest) {
        KeyPair keyPair;
        try {
            keyPair = getAccountKeyPair(memberCode);
        } catch (GeneralSecurityException | OperatorCreationException | IOException e) {
            throw new AcmeServiceException(ACCOUNT_KEY_PAIR_ERROR, e);
        }

        Account account;
        try {
            account = startSession(caInfo, keyPair, memberCode);
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

    private KeyPair getAccountKeyPair(String memberCode) throws GeneralSecurityException, IOException, OperatorCreationException {
        File acmeKeystoreFile = new File(acmeAccountKeystorePath);
        char[] keystorePassword = acmeAccountKeystorePassword.toCharArray();
        KeyStore keyStore;
        if (acmeKeystoreFile.exists()) {
            keyStore = CryptoUtils.loadPkcs12KeyStore(acmeKeystoreFile, keystorePassword);
        } else {
            keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(null, keystorePassword);
        }
        X509Certificate certificate = (X509Certificate) keyStore.getCertificate(memberCode);
        KeyPair keyPair;
        if (certificate != null) {
            log.debug("Loading keypair");
            PublicKey publicKey = certificate.getPublicKey();
            PrivateKey privateKey = (PrivateKey) keyStore.getKey(memberCode, memberCode.toCharArray());
            keyPair = new KeyPair(publicKey, privateKey);
        } else {
            log.debug("Creating keypair");
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(SystemProperties.getSignerKeyLength(), new SecureRandom());
            keyPair = keyPairGenerator.generateKeyPair();

            X509Certificate[] certificateChain = createSelfSignedCertificate(memberCode, keyPair);

            keyStore.setKeyEntry(
                    memberCode,
                    keyPair.getPrivate(),
                    memberCode.toCharArray(),
                    certificateChain);
            try (OutputStream outputStream = new FileOutputStream(acmeKeystoreFile)) {
                keyStore.store(outputStream, keystorePassword);
                outputStream.flush();
            }
        }
        return keyPair;
    }

    private Account startSession(ApprovedCAInfo caInfo, KeyPair keyPair, String memberCode) throws AcmeException {
        log.info("Creating session with directory url: {}", caInfo.getAcmeServerDirectoryUrl());
        Session session = new Session(caInfo.getAcmeServerDirectoryUrl());
        Metadata metadata = getMetadata(session);
        log.debug("ACME server metadata: {}", metadata.getJSON().toString());
        log.debug("Creating account");
        AccountBuilder accountBuilder = new AccountBuilder()
                .agreeToTermsOfService()
                .useKeyPair(keyPair);
        Optional.ofNullable(acmeEabProperties.getContacts())
                .map(AcmeEabProperties.Contacts::getMembers)
                .map(members -> members.get(memberCode))
                .map(AcmeEabProperties.Contact::getEmail)
                .ifPresent(accountBuilder::addContact);
        if (metadata.isExternalAccountRequired()) {
            AcmeEabProperties.Credentials credential =
                    Optional.ofNullable(acmeEabProperties.getEabCredentials())
                            .map(AcmeEabProperties.EabCredentials::getCertificateAuthorities)
                            .map(certAuthorities -> certAuthorities.get(caInfo.getName()))
                            .map(AcmeEabProperties.CA::getMembers)
                            .map(members -> members.get(memberCode))
                            .orElseThrow(() -> new NotFoundException(EAB_CREDENTIALS_MISSING));
            String secret = credential.getMacKey();
            String secretWithPadding = padBase64(secret);
            accountBuilder.withKeyIdentifier(credential.getKid(), secretWithPadding);
            accountBuilder.withMacAlgorithm(AlgorithmIdentifiers.HMAC_SHA256);
        }
        return accountBuilder.create(session);
    }

    private static Metadata getMetadata(Session session) {
        Metadata metadata;
        try {
            metadata = session.getMetadata();
        } catch (AcmeException e) {
            throw new AcmeServiceException(FETCHING_METADATA_ERROR, e);
        }
        return metadata;
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
                waitForTheChallengeToBeCompleted(auth);
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

    private static void waitForTheChallengeToBeCompleted(Authorization auth) {
        log.debug("Waiting for challenge to be completed");
        int attempts = SystemProperties.getAcmeAuthorizationWaitAttempts();
        long interval = SystemProperties.getAcmeAuthorizationWaitInterval();
        while (auth.getStatus() != Status.VALID  && attempts-- > 0) {
            if (auth.getStatus() == Status.INVALID) {
                throw new AcmeServiceException(AUTHORIZATION_FAILURE);
            }
            try {
                TimeUnit.MILLISECONDS.sleep(interval);
                auth.update();
            } catch (AcmeRetryAfterException e) {
                log.debug("Retrying authorization after {}", e.getRetryAfter());
                interval = Instant.now().until(e.getRetryAfter(), ChronoUnit.SECONDS);
            } catch (AcmeException e) {
                log.error("Authorization failed", e);
                throw new AcmeServiceException(AUTHORIZATION_FAILURE, e);
            } catch (InterruptedException e) {
                throw new AcmeServiceException(AUTHORIZATION_WAIT_FAILURE, e);
            }
        }
    }

    private Certificate getCertificate(Order order) {
        log.debug("Getting the certificate");
        int attempts = SystemProperties.getAcmeCertificateWaitAttempts();
        long interval = SystemProperties.getAcmeCertificateWaitInterval();
        while (order.getStatus() != Status.VALID && attempts-- > 0) {
            if (order.getStatus() == Status.INVALID) {
                throw new AcmeServiceException(CERTIFICATE_FAILURE);
            }
            try {
                TimeUnit.MILLISECONDS.sleep(interval);
                order.update();
            } catch (AcmeRetryAfterException e) {
                log.debug("Retrying completing order after {}", e.getRetryAfter());
                interval = Instant.now().until(e.getRetryAfter(), ChronoUnit.SECONDS);
            } catch (AcmeException e) {
                log.error("Certificate creation failed", e);
                throw new AcmeServiceException(CERTIFICATE_FAILURE, e);
            } catch (InterruptedException e) {
                throw new AcmeServiceException(CERTIFICATE_WAIT_FAILURE, e);
            }
        }

        return order.getCertificate();
    }
}
