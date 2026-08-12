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
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.ExtensionsGenerator;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.niis.xroad.common.acme.AcmeAccountContext;
import org.niis.xroad.common.acme.AcmeChallengeSettings;
import org.niis.xroad.common.acme.AcmeClient;
import org.niis.xroad.common.acme.AcmeEabCredentials;
import org.niis.xroad.common.acme.AcmeServiceException;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.model.ApprovedDsTlsCaInfo;
import org.springframework.stereotype.Service;

import javax.security.auth.x500.X500Principal;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
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
import java.time.temporal.ChronoUnit;
import java.util.List;

import static ee.ria.xroad.common.util.CertUtils.createSelfSignedCertificate;
import static org.apache.commons.lang3.ArrayUtils.isEmpty;

/**
 * Re-layers the dataspace TLS certificate's ACME enrollment and renewal on top of the shared {@link AcmeClient},
 * mirroring {@link AcmeService}'s member-cert re-layering but for a transport certificate: no member id, no signer
 * key-usage type, no {@link org.niis.xroad.globalconf.model.ApprovedCAInfo}. The dataspace TLS key pair and CSR are
 * generated here, in software - the ADR's service-owned choice keeps signer untouched.
 * <p>
 * Uses a fixed, non-member ACME account alias ({@link #ACCOUNT_ALIAS}), safe from ever colliding with a member-cert
 * alias because an encoded member id always contains a {@code :} separator. Shares the member-cert flow's account
 * keystore file and password ({@link AcmeConfig#ACME_ACCOUNT_KEYSTORE_PATH}, {@link AcmeProperties}) under that
 * alias - no new configuration schema.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public final class DsTlsAcmeService {

    public static final String ACCOUNT_ALIAS = "dataspace-tls";

    private final AcmeProperties acmeProperties;
    private final AcmeConfig acmeConfig;
    private final AcmeClient acmeClient;

    public List<X509Certificate> orderCertificate(ApprovedDsTlsCaInfo ca, String hostname, byte[] certRequest) {
        return orderCertificate(AcmeConfig.ACME_ACCOUNT_KEYSTORE_PATH.toFile(), AcmeConfig.ACME_CHALLENGE_PATH, ca, hostname, certRequest);
    }

    /**
     * Package-private seam for tests: {@link #orderCertificate(ApprovedDsTlsCaInfo, String, byte[])} always targets
     * the real account keystore and challenge directories; this overload lets tests point both at locations of
     * their own, since {@link AcmeConfig#ACME_ACCOUNT_KEYSTORE_PATH} and {@link AcmeConfig#ACME_CHALLENGE_PATH} are
     * hardcoded, non-configurable filesystem paths.
     */
    List<X509Certificate> orderCertificate(File acmeKeystoreFile, Path challengeDirectory, ApprovedDsTlsCaInfo ca,
                                           String hostname, byte[] certRequest) {
        AcmeAccountContext account = accountContext(acmeKeystoreFile, ca);
        return acmeClient.orderCertificate(account, hostname, hostname, certRequest, buildChallengeSettings(challengeDirectory));
    }

    public List<X509Certificate> renew(ApprovedDsTlsCaInfo ca, String hostname, X509Certificate certificateToReplace,
                                       byte[] certRequest) {
        return renew(AcmeConfig.ACME_ACCOUNT_KEYSTORE_PATH.toFile(), AcmeConfig.ACME_CHALLENGE_PATH, ca, hostname,
                certificateToReplace, certRequest);
    }

    /**
     * Package-private seam for tests: see
     * {@link #orderCertificate(File, Path, ApprovedDsTlsCaInfo, String, byte[])}.
     */
    List<X509Certificate> renew(File acmeKeystoreFile, Path challengeDirectory, ApprovedDsTlsCaInfo ca, String hostname,
                                X509Certificate certificateToReplace, byte[] certRequest) {
        AcmeAccountContext account = accountContext(acmeKeystoreFile, ca);
        AcmeChallengeSettings challengeSettings = buildChallengeSettings(challengeDirectory);
        return acmeClient.renewCertificate(account, hostname, certificateToReplace, certRequest, challengeSettings);
    }

    public boolean hasRenewalInfo(ApprovedDsTlsCaInfo ca) {
        return hasRenewalInfo(AcmeConfig.ACME_ACCOUNT_KEYSTORE_PATH.toFile(), ca);
    }

    /**
     * Package-private seam for tests: see {@link #checkAccountKeyPairAndRenewIfNecessary(File, ApprovedDsTlsCaInfo)}.
     */
    boolean hasRenewalInfo(File acmeKeystoreFile, ApprovedDsTlsCaInfo ca) {
        return acmeClient.hasRenewalInfo(accountContext(acmeKeystoreFile, ca));
    }

    public boolean isRenewalRequired(ApprovedDsTlsCaInfo ca, X509Certificate certificate) {
        return isRenewalRequired(AcmeConfig.ACME_ACCOUNT_KEYSTORE_PATH.toFile(), ca, certificate);
    }

    /**
     * Package-private seam for tests: see {@link #checkAccountKeyPairAndRenewIfNecessary(File, ApprovedDsTlsCaInfo)}.
     */
    boolean isRenewalRequired(File acmeKeystoreFile, ApprovedDsTlsCaInfo ca, X509Certificate certificate) {
        return acmeClient.isRenewalRequired(accountContext(acmeKeystoreFile, ca), certificate);
    }

    public Instant getNextRenewalTime(ApprovedDsTlsCaInfo ca, X509Certificate certificate) {
        return getNextRenewalTime(AcmeConfig.ACME_ACCOUNT_KEYSTORE_PATH.toFile(), ca, certificate);
    }

    /**
     * Package-private seam for tests: see {@link #checkAccountKeyPairAndRenewIfNecessary(File, ApprovedDsTlsCaInfo)}.
     */
    Instant getNextRenewalTime(File acmeKeystoreFile, ApprovedDsTlsCaInfo ca, X509Certificate certificate) {
        AcmeAccountContext account = accountContext(acmeKeystoreFile, ca);
        return acmeClient.getNextRenewalTime(account, certificate, acmeConfig.getAcmeRenewalTimeBeforeExpirationDate());
    }

    /**
     * Generates a fresh software RSA key pair for the dataspace TLS certificate itself. A new pair is generated for
     * every enrollment and every renewal - never reused across certificate lifetimes.
     */
    public KeyPair generateKeyPair() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(acmeConfig.getAcmeKeyLength(), new SecureRandom());
            return keyPairGenerator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw XrdRuntimeException.systemException(e);
        }
    }

    /**
     * Builds a PKCS#10 CSR for {@code hostname}, carrying it both as the subject common name and as a DNS subject
     * alternative name extension.
     */
    public byte[] generateCsr(KeyPair keyPair, String hostname) {
        try {
            X500Principal subject = new X500Principal("CN=" + hostname);
            var builder = new JcaPKCS10CertificationRequestBuilder(subject, keyPair.getPublic());
            ExtensionsGenerator extensionsGenerator = new ExtensionsGenerator();
            GeneralNames subjectAltNames = new GeneralNames(new GeneralName(GeneralName.dNSName, hostname));
            extensionsGenerator.addExtension(Extension.subjectAlternativeName, false, subjectAltNames);
            builder.addAttribute(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest, extensionsGenerator.generate());
            ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
            return builder.build(signer).getEncoded();
        } catch (Exception e) {
            throw XrdRuntimeException.systemException(e);
        }
    }

    public void checkAccountKeyPairAndRenewIfNecessary(ApprovedDsTlsCaInfo ca) {
        checkAccountKeyPairAndRenewIfNecessary(AcmeConfig.ACME_ACCOUNT_KEYSTORE_PATH.toFile(), ca);
    }

    /**
     * Package-private seam for tests: {@link #checkAccountKeyPairAndRenewIfNecessary(ApprovedDsTlsCaInfo)} always
     * targets the real account keystore path; this overload lets tests point it at a keystore file of their own.
     */
    void checkAccountKeyPairAndRenewIfNecessary(File acmeKeystoreFile, ApprovedDsTlsCaInfo ca) {
        try {
            KeyPair currentAccountKeyPair = getAccountKeyPair(acmeKeystoreFile, ca);
            char[] storePassword = acmeProperties.getAccountKeystorePassword();
            KeyStore keyStore = CryptoUtils.loadPkcs12KeyStore(acmeKeystoreFile, storePassword);
            X509Certificate wrapperCertificate = (X509Certificate) keyStore.getCertificate(ACCOUNT_ALIAS);
            int renewalTimeBeforeExpirationDate = acmeConfig.getAcmeKeypairRenewalTimeBeforeExpirationDate();
            if (wrapperCertificate != null && Instant.now()
                    .isAfter(wrapperCertificate.getNotAfter().toInstant().minus(renewalTimeBeforeExpirationDate, ChronoUnit.DAYS))) {
                KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
                keyPairGenerator.initialize(acmeConfig.getAcmeKeyLength(), new SecureRandom());
                KeyPair newAccountKeyPair = keyPairGenerator.generateKeyPair();

                AcmeAccountContext account = buildAccountContext(ca, currentAccountKeyPair);
                acmeClient.changeAccountKey(account, newAccountKeyPair);

                long expirationInDays = acmeConfig.getAcmeCertificateAccountKeyPairExpiration();
                X509Certificate[] certificateChain = createSelfSignedCertificate(ACCOUNT_ALIAS, newAccountKeyPair, expirationInDays);
                keyStore.setKeyEntry(ACCOUNT_ALIAS, newAccountKeyPair.getPrivate(), ACCOUNT_ALIAS.toCharArray(), certificateChain);
                try (OutputStream outputStream = new FileOutputStream(acmeKeystoreFile)) {
                    keyStore.store(outputStream, storePassword);
                    outputStream.flush();
                }
                log.info("Renewed ACME account keypair for the dataspace TLS certificate");
            }
        } catch (Exception e) {
            log.error("Renewing dataspace TLS ACME account key pair failed", e);
        }
    }

    private AcmeAccountContext accountContext(File acmeKeystoreFile, ApprovedDsTlsCaInfo ca) {
        return buildAccountContext(ca, getAccountKeyPair(acmeKeystoreFile, ca));
    }

    private AcmeAccountContext buildAccountContext(ApprovedDsTlsCaInfo ca, KeyPair accountKeyPair) {
        return new AcmeAccountContext(
                ca.getAcmeServerDirectoryUrl(),
                accountKeyPair,
                () -> resolveEabCredentials(ca),
                null,
                ca.getDsTlsCertificateProfileId());
    }

    private AcmeChallengeSettings buildChallengeSettings(Path challengeDirectory) {
        return new AcmeChallengeSettings(
                challengeDirectory,
                acmeConfig.getAcmeAuthorizationWaitAttempts(),
                acmeConfig.getAcmeAuthorizationWaitInterval(),
                acmeConfig.getAcmeCertificateWaitAttempts(),
                acmeConfig.getAcmeCertificateWaitInterval());
    }

    private AcmeEabCredentials resolveEabCredentials(ApprovedDsTlsCaInfo ca) {
        AcmeProperties.Credentials credentials = acmeProperties.getEabCredentials(ca.getName(), ACCOUNT_ALIAS);
        return new AcmeEabCredentials(credentials.getKid(), credentials.getMacKey(), acmeProperties.isEabMacKeyBase64Encoded(ca.getName()));
    }

    /**
     * Package-private seam for tests: see {@link #checkAccountKeyPairAndRenewIfNecessary(File, ApprovedDsTlsCaInfo)}.
     */
    KeyPair getAccountKeyPair(File acmeKeystoreFile, ApprovedDsTlsCaInfo ca) {
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
            X509Certificate certificate = (X509Certificate) keyStore.getCertificate(ACCOUNT_ALIAS);
            KeyPair keyPair;
            if (certificate != null) {
                log.debug("Loading dataspace TLS ACME account keypair");
                PublicKey publicKey = certificate.getPublicKey();
                PrivateKey privateKey = (PrivateKey) keyStore.getKey(ACCOUNT_ALIAS, ACCOUNT_ALIAS.toCharArray());
                keyPair = new KeyPair(publicKey, privateKey);
            } else {
                log.debug("Creating dataspace TLS ACME account keypair");
                KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
                keyPairGenerator.initialize(acmeConfig.getAcmeKeyLength(), new SecureRandom());
                keyPair = keyPairGenerator.generateKeyPair();

                long expirationInDays = acmeConfig.getAcmeCertificateAccountKeyPairExpiration();
                X509Certificate[] certificateChain = createSelfSignedCertificate(ACCOUNT_ALIAS, keyPair, expirationInDays);

                keyStore.setKeyEntry(ACCOUNT_ALIAS, keyPair.getPrivate(), ACCOUNT_ALIAS.toCharArray(), certificateChain);
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

}
