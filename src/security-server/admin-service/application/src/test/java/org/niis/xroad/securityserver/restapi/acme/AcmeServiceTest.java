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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.niis.xroad.common.acme.AcmeAccountContext;
import org.niis.xroad.common.acme.AcmeClient;
import org.niis.xroad.common.acme.testsupport.AcmeTestFixtures;
import org.niis.xroad.common.acme.testsupport.FakeAcmeServer;
import org.niis.xroad.globalconf.model.ApprovedCAInfo;
import org.niis.xroad.globalconf.model.CsrFormat;
import org.niis.xroad.securityserver.restapi.mail.MailNotificationProperties;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link AcmeService} directly (no Spring context) against a real {@link FakeAcmeServer} and a real
 * PKCS12 file on disk, for the two seams that {@link org.niis.xroad.securityserver.restapi.scheduling.AcmeClientWorkerTest}
 * cannot reach because it mocks {@code acmeService} out entirely: account key-pair rotation persistence, and the
 * certificate-profile-id fall-back to the plain ACME scheme.
 */
class AcmeServiceTest {

    private static final char[] STORE_PASSWORD = "test-keystore-password-1".toCharArray();
    private static final String CA_NAME = "test-ca";
    private static final String MEMBER_ID = "TEST:GOV:1234:MEMBER";
    private static final int KEY_LENGTH = 2048;

    private final AcmeClient acmeClient = new AcmeClient();

    @Test
    void rotatingAccountKeyPairPersistsNewKeyToKeystoreFile(@TempDir Path tempDir) throws Exception {
        Path keystoreFile = tempDir.resolve("acme-account.p12");
        KeyPair oldAccountKeyPair = AcmeTestFixtures.rsaKeyPair();
        seedExpiredAccountKeyPair(keystoreFile, MEMBER_ID, oldAccountKeyPair);

        try (FakeAcmeServer server = new FakeAcmeServer(tempDir.resolve("challenges"))) {
            ApprovedCAInfo caInfo = approvedCa(server.directoryUrl(), null, null);
            AcmeService acmeService = new AcmeService(acmePropertiesWithEab(), new MailNotificationProperties(),
                    acmeConfig(30), acmeClient);

            acmeService.checkAccountKeyPairAndRenewIfNecessary(keystoreFile.toFile(), MEMBER_ID, caInfo, KeyUsageInfo.AUTHENTICATION);

            KeyStore reloaded = KeyStore.getInstance("PKCS12");
            try (InputStream in = Files.newInputStream(keystoreFile)) {
                reloaded.load(in, STORE_PASSWORD);
            }
            X509Certificate reloadedCertificate = (X509Certificate) reloaded.getCertificate(MEMBER_ID);
            PrivateKey reloadedPrivateKey = (PrivateKey) reloaded.getKey(MEMBER_ID, MEMBER_ID.toCharArray());

            assertThat(reloadedCertificate).isNotNull();
            // proves the file was actually rewritten with the rotated key, not left holding the pre-rotation
            // material - this is exactly what "keyStore.store() was never called" would fail to do.
            assertThat(reloadedCertificate.getPublicKey()).isNotEqualTo(oldAccountKeyPair.getPublic());
            assertKeyPairMatches(reloadedPrivateKey, reloadedCertificate.getPublicKey());
        }
    }

    @Test
    void fallsBackToPlainSchemeWhenSigningProfileIdIsMissing() throws Exception {
        AcmeService acmeService = new AcmeService(acmePropertiesWithEab(), new MailNotificationProperties(),
                mock(AcmeConfig.class), acmeClient);
        // the CA has an authentication profile id configured but no signing one - ordering a SIGNING certificate
        // must not route through the profile-id scheme with an unresolved (null) id.
        ApprovedCAInfo caInfo = approvedCa("http://example.invalid/directory", "auth-profile-id", null);

        AcmeAccountContext context =
                acmeService.buildAccountContext(MEMBER_ID, KeyUsageInfo.SIGNING, caInfo, AcmeTestFixtures.rsaKeyPair());

        assertThat(context.certificateProfileId()).isNull();
    }

    @Test
    void resolvesConfiguredProfileIdForTheRequestedKeyUsage() throws Exception {
        AcmeService acmeService = new AcmeService(acmePropertiesWithEab(), new MailNotificationProperties(),
                mock(AcmeConfig.class), acmeClient);
        ApprovedCAInfo caInfo = approvedCa("http://example.invalid/directory", "auth-profile-id", "sign-profile-id");

        AcmeAccountContext signingContext =
                acmeService.buildAccountContext(MEMBER_ID, KeyUsageInfo.SIGNING, caInfo, AcmeTestFixtures.rsaKeyPair());
        AcmeAccountContext authContext =
                acmeService.buildAccountContext(MEMBER_ID, KeyUsageInfo.AUTHENTICATION, caInfo, AcmeTestFixtures.rsaKeyPair());

        assertThat(signingContext.certificateProfileId()).isEqualTo("sign-profile-id");
        assertThat(authContext.certificateProfileId()).isEqualTo("auth-profile-id");
    }

    private static void assertKeyPairMatches(PrivateKey privateKey, PublicKey publicKey) throws Exception {
        byte[] challenge = "acme-account-key-rotation-check".getBytes(StandardCharsets.UTF_8);

        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(privateKey);
        signer.update(challenge);
        byte[] signature = signer.sign();

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(publicKey);
        verifier.update(challenge);
        assertThat(verifier.verify(signature)).isTrue();
    }

    private static void seedExpiredAccountKeyPair(Path keystoreFile, String alias, KeyPair keyPair) throws Exception {
        X509Certificate expiredWrapperCert = AcmeTestFixtures.selfSignedCertificate(keyPair, alias,
                Instant.now().minus(400, ChronoUnit.DAYS), Instant.now().minus(1, ChronoUnit.DAYS));
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, STORE_PASSWORD);
        keyStore.setKeyEntry(alias, keyPair.getPrivate(), alias.toCharArray(), new X509Certificate[] {expiredWrapperCert});
        try (OutputStream out = Files.newOutputStream(keystoreFile)) {
            keyStore.store(out, STORE_PASSWORD);
        }
    }

    private static ApprovedCAInfo approvedCa(String directoryUrl, String authProfileId, String signProfileId) {
        return new ApprovedCAInfo(CA_NAME, false, null, CsrFormat.PEM, directoryUrl, null, authProfileId, signProfileId);
    }

    private static AcmeConfig acmeConfig(int renewalTimeBeforeExpirationDate) {
        AcmeConfig acmeConfig = mock(AcmeConfig.class);
        when(acmeConfig.getAcmeKeyLength()).thenReturn(KEY_LENGTH);
        when(acmeConfig.getAcmeCertificateAccountKeyPairExpiration()).thenReturn(365);
        when(acmeConfig.getAcmeKeypairRenewalTimeBeforeExpirationDate()).thenReturn(renewalTimeBeforeExpirationDate);
        return acmeConfig;
    }

    private static AcmeProperties acmePropertiesWithEab() {
        AcmeProperties.Credentials credentials = new AcmeProperties.Credentials();
        credentials.setKid("test-kid");
        credentials.setMacKey(AcmeTestFixtures.encodeBase64Url(AcmeTestFixtures.randomBytes(32)));

        AcmeProperties.CA ca = new AcmeProperties.CA();
        ca.setMacKeyBase64Encoded(true);
        Map<String, AcmeProperties.Credentials> members = new HashMap<>();
        members.put(MEMBER_ID, credentials);
        ca.setMembers(members);

        Map<String, AcmeProperties.CA> certificateAuthorities = new HashMap<>();
        certificateAuthorities.put(CA_NAME, ca);
        AcmeProperties.EabCredentials eabCredentials = new AcmeProperties.EabCredentials();
        eabCredentials.setCertificateAuthorities(certificateAuthorities);

        AcmeProperties acmeProperties = new AcmeProperties();
        acmeProperties.setEabCredentials(eabCredentials);
        acmeProperties.setAccountKeystorePassword(new String(STORE_PASSWORD));
        return acmeProperties;
    }

}
