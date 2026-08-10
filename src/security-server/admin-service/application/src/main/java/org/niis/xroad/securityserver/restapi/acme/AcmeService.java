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
package org.niis.xroad.securityserver.restapi.acme;

import ee.ria.xroad.common.util.CryptoUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.operator.OperatorCreationException;
import org.niis.xroad.common.acme.AcmeAccountContext;
import org.niis.xroad.common.acme.AcmeChallengeSettings;
import org.niis.xroad.common.acme.AcmeClient;
import org.niis.xroad.common.acme.AcmeEabCredentials;
import org.niis.xroad.common.acme.AcmeServiceException;
import org.niis.xroad.globalconf.model.ApprovedCAInfo;
import org.niis.xroad.securityserver.restapi.mail.MailNotificationProperties;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static ee.ria.xroad.common.util.CertUtils.createSelfSignedCertificate;
import static org.apache.commons.lang3.ArrayUtils.isEmpty;

/**
 * Re-layers the Security Server's member-cert ACME enrollment and renewal on top of the shared {@link AcmeClient}.
 * Everything keyed on member id, signer key-usage type or member-cert CA metadata stays here; the ACME protocol
 * itself - account/session handling, order placement, EAB, HTTP-01 fulfilment, ARI-aware renewal decisions - is the
 * shared core's job.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public final class AcmeService {

    private final AcmeProperties acmeProperties;
    private final MailNotificationProperties mailNotificationProperties;
    private final AcmeConfig acmeConfig;
    private final AcmeClient acmeClient;

    public boolean isExternalAccountBindingRequired(String acmeServerDirectoryUrl) {
        return acmeClient.isExternalAccountBindingRequired(acmeServerDirectoryUrl);
    }

    public List<X509Certificate> orderCertificateFromACMEServer(String commonName,
                                                                String subjectAltName,
                                                                KeyUsageInfo keyUsage,
                                                                ApprovedCAInfo caInfo,
                                                                String memberId,
                                                                byte[] certRequest) {
        KeyPair keyPair = getAccountKeyPair(memberId, keyUsage, caInfo);
        AcmeAccountContext account = buildAccountContext(memberId, keyUsage, caInfo, keyPair);
        return acmeClient.orderCertificate(account, commonName, subjectAltName, certRequest, buildChallengeSettings());
    }

    public void checkAccountKeyPairAndRenewIfNecessary(String memberId, ApprovedCAInfo caInfo, KeyUsageInfo keyUsage) {
        try {
            KeyPair currentAccountKeyPair = getAccountKeyPair(memberId, keyUsage, caInfo);
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
                KeyPair newAccountKeyPair = keyPairGenerator.generateKeyPair();

                AcmeAccountContext account = buildAccountContext(memberId, keyUsage, caInfo, currentAccountKeyPair);
                acmeClient.changeAccountKey(account, newAccountKeyPair);

                long expirationInDays = acmeConfig.getAcmeCertificateAccountKeyPairExpiration();
                X509Certificate[] certificateChain = createSelfSignedCertificate(alias, newAccountKeyPair, expirationInDays);
                keyStore.setKeyEntry(
                        alias,
                        newAccountKeyPair.getPrivate(),
                        alias.toCharArray(),
                        certificateChain);
                try (OutputStream outputStream = new FileOutputStream(acmeKeystoreFile)) {
                    keyStore.store(outputStream, storePassword);
                    outputStream.flush();
                }
                log.info("Renewed acme account keypair for {}", memberId);
            }
        } catch (Exception e) {
            log.error("Renewing account key pair failed", e);
        }
    }

    public boolean hasRenewalInfo(String memberId, ApprovedCAInfo approvedCA, KeyUsageInfo keyUsage) {
        AcmeAccountContext account = buildAccountContext(memberId, keyUsage, approvedCA, getAccountKeyPair(memberId, keyUsage, approvedCA));
        return acmeClient.hasRenewalInfo(account);
    }

    public boolean isRenewalRequired(String memberId, ApprovedCAInfo approvedCA, X509Certificate certificate, KeyUsageInfo keyUsage) {
        AcmeAccountContext account = buildAccountContext(memberId, keyUsage, approvedCA, getAccountKeyPair(memberId, keyUsage, approvedCA));
        return acmeClient.isRenewalRequired(account, certificate);
    }

    public Instant getNextRenewalTime(String memberId, ApprovedCAInfo approvedCA, X509Certificate x509Certificate, KeyUsageInfo keyUsage) {
        AcmeAccountContext account = buildAccountContext(memberId, keyUsage, approvedCA, getAccountKeyPair(memberId, keyUsage, approvedCA));
        int fallbackDaysBeforeExpiration = acmeConfig.getAcmeRenewalTimeBeforeExpirationDate();
        return acmeClient.getNextRenewalTime(account, x509Certificate, fallbackDaysBeforeExpiration);
    }

    public List<X509Certificate> renew(String memberId, String subjectAltName, ApprovedCAInfo approvedCA,
                                       KeyUsageInfo keyUsage,
                                       X509Certificate oldCertificate, byte[] newCsr) {
        AcmeAccountContext account = buildAccountContext(memberId, keyUsage, approvedCA, getAccountKeyPair(memberId, keyUsage, approvedCA));
        return acmeClient.renewCertificate(account, subjectAltName, oldCertificate, newCsr, buildChallengeSettings());
    }

    private AcmeAccountContext buildAccountContext(String memberId, KeyUsageInfo keyUsage, ApprovedCAInfo caInfo, KeyPair accountKeyPair) {
        String contactUri = Optional.ofNullable(mailNotificationProperties.getContacts())
                .map(contacts -> contacts.get(memberId))
                .orElse(null);
        boolean caUsesProfileIds = caInfo.getAuthenticationCertificateProfileId() != null;
        String certificateProfileId = !caUsesProfileIds ? null
                : keyUsage == KeyUsageInfo.SIGNING ? caInfo.getSigningCertificateProfileId()
                : caInfo.getAuthenticationCertificateProfileId();
        return new AcmeAccountContext(
                caInfo.getAcmeServerDirectoryUrl(),
                accountKeyPair,
                () -> resolveEabCredentials(memberId, keyUsage, caInfo),
                contactUri,
                certificateProfileId);
    }

    private AcmeChallengeSettings buildChallengeSettings() {
        return new AcmeChallengeSettings(
                AcmeConfig.ACME_CHALLENGE_PATH,
                acmeConfig.getAcmeAuthorizationWaitAttempts(),
                acmeConfig.getAcmeAuthorizationWaitInterval(),
                acmeConfig.getAcmeCertificateWaitAttempts(),
                acmeConfig.getAcmeCertificateWaitInterval());
    }

    private AcmeEabCredentials resolveEabCredentials(String memberId, KeyUsageInfo keyUsage, ApprovedCAInfo caInfo) {
        AcmeProperties.Credentials credential = acmeProperties.getEabCredentials(caInfo.getName(), memberId);
        String kid;
        String secret;
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
        return new AcmeEabCredentials(kid, secret, acmeProperties.isEabMacKeyBase64Encoded(caInfo.getName()));
    }

    private KeyPair getAccountKeyPair(String memberId, KeyUsageInfo keyUsage, ApprovedCAInfo caInfo) {
        String alias = getAlias(memberId, keyUsage, caInfo);
        File acmeKeystoreFile = AcmeConfig.ACME_ACCOUNT_KEYSTORE_PATH.toFile();
        KeyStore keyStore;
        char[] storePassword = acmeProperties.getAccountKeystorePassword();
        if (isEmpty(storePassword)) {
            if (acmeKeystoreFile.exists()) {
                throw new AcmeServiceException(AcmeDeviationMessage.ACCOUNT_KEYSTORE_PASSWORD_MISSING.build());
            } else {
                storePassword = acmeProperties.createNewAccountKeystorePassword();
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

}
