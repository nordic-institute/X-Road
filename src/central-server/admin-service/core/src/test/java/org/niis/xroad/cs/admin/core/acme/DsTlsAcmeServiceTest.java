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
package org.niis.xroad.cs.admin.core.acme;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.niis.xroad.common.acme.AcmeClient;
import org.niis.xroad.common.acme.testsupport.AcmeTestFixtures;
import org.niis.xroad.common.acme.testsupport.FakeAcmeServer;
import org.niis.xroad.cs.admin.api.dto.DsTlsCa;

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
import java.util.List;
import java.util.Map;

import static ee.ria.xroad.common.util.CertUtils.getSubjectAlternativeNameFromCsr;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link DsTlsAcmeService} against a real {@link FakeAcmeServer}, mirroring the Security Server
 * equivalent's coverage of the seams a mocked {@code dsTlsAcmeService} in {@link DsTlsAcmeEnrollmentWorkerTest}
 * cannot reach: CSR/order plumbing against a real ACME exchange, and account key-pair rotation persistence under
 * the fixed dataspace-tls alias.
 * <p>
 * Every call that resolves an ACME login (renewal-info, renew, account key-change) unconditionally consults EAB
 * credentials once an {@link org.niis.xroad.common.acme.AcmeAccountContext} carries a (lazy) supplier for them -
 * see {@link DsTlsAcmeService#renew} and friends - so every test below configures EAB credentials even against a
 * fake server that does not itself require External Account Binding.
 */
class DsTlsAcmeServiceTest {

    private static final char[] STORE_PASSWORD = "test-keystore-password-1".toCharArray();
    private static final String CA_NAME = "test-ds-tls-ca";
    private static final String HOSTNAME = "cs1.example.org";
    private static final int KEY_LENGTH = 2048;

    private final AcmeClient acmeClient = new AcmeClient();

    @Test
    void generatesCsrCarryingHostnameAsSanAndCommonName() throws Exception {
        DsTlsAcmeService service = new DsTlsAcmeService(new AcmeProperties(), acmeConfig(30), acmeClient);
        KeyPair keyPair = AcmeTestFixtures.rsaKeyPair();

        byte[] csr = service.generateCsr(keyPair, HOSTNAME);

        assertThat(getSubjectAlternativeNameFromCsr(csr)).isEqualTo(HOSTNAME);
    }

    @Test
    void ordersCertificateThroughFakeAcmeServerWithEab(@TempDir Path tempDir) throws Exception {
        Path keystoreFile = tempDir.resolve("acme-account.p12");
        Path challengeDir = tempDir.resolve("challenges");
        byte[] macSecret = AcmeTestFixtures.randomBytes(32);
        try (FakeAcmeServer server = new FakeAcmeServer(challengeDir)) {
            server.requireExternalAccountBinding("test-kid", macSecret);
            DsTlsAcmeService service = new DsTlsAcmeService(acmePropertiesWithEab(macSecret), acmeConfig(30), acmeClient);
            DsTlsCa ca = approvedDsTlsCa(server.directoryUrl());

            KeyPair domainKeyPair = AcmeTestFixtures.rsaKeyPair();
            byte[] csr = service.generateCsr(domainKeyPair, HOSTNAME);

            List<X509Certificate> chain = service.orderCertificate(keystoreFile.toFile(), challengeDir, ca, HOSTNAME, csr);

            assertThat(chain).isNotNull().hasSize(2);
            assertThat(chain.getFirst().getSubjectX500Principal().getName()).contains("CN=" + HOSTNAME);
        }
    }

    @Test
    void renewsCertificateThroughFakeAcmeServer(@TempDir Path tempDir) throws Exception {
        Path keystoreFile = tempDir.resolve("acme-account.p12");
        Path challengeDir = tempDir.resolve("challenges");
        byte[] macSecret = AcmeTestFixtures.randomBytes(32);
        try (FakeAcmeServer server = new FakeAcmeServer(challengeDir)) {
            DsTlsAcmeService service = new DsTlsAcmeService(acmePropertiesWithEab(macSecret), acmeConfig(30), acmeClient);
            DsTlsCa ca = approvedDsTlsCa(server.directoryUrl());

            KeyPair firstKeyPair = AcmeTestFixtures.rsaKeyPair();
            List<X509Certificate> firstChain = service.orderCertificate(keystoreFile.toFile(), challengeDir, ca, HOSTNAME,
                    service.generateCsr(firstKeyPair, HOSTNAME));

            KeyPair renewedKeyPair = AcmeTestFixtures.rsaKeyPair();
            List<X509Certificate> renewedChain = service.renew(keystoreFile.toFile(), challengeDir, ca, HOSTNAME,
                    firstChain.getFirst(), service.generateCsr(renewedKeyPair, HOSTNAME));

            assertThat(renewedChain).isNotNull().hasSize(2);
            assertThat(renewedChain.getFirst().getPublicKey()).isEqualTo(renewedKeyPair.getPublic());
        }
    }

    @Test
    void reportsRenewalDueFromAriSuggestedWindow(@TempDir Path tempDir) throws Exception {
        Path keystoreFile = tempDir.resolve("acme-account.p12");
        Path challengeDir = tempDir.resolve("challenges");
        byte[] macSecret = AcmeTestFixtures.randomBytes(32);
        try (FakeAcmeServer server = new FakeAcmeServer(challengeDir)) {
            server.setRenewalWindow(Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS));
            DsTlsAcmeService service = new DsTlsAcmeService(acmePropertiesWithEab(macSecret), acmeConfig(30), acmeClient);
            DsTlsCa ca = approvedDsTlsCa(server.directoryUrl());

            List<X509Certificate> chain = service.orderCertificate(keystoreFile.toFile(), challengeDir, ca, HOSTNAME,
                    service.generateCsr(AcmeTestFixtures.rsaKeyPair(), HOSTNAME));

            assertThat(service.hasRenewalInfo(keystoreFile.toFile(), ca)).isTrue();
            assertThat(service.isRenewalRequired(keystoreFile.toFile(), ca, chain.getFirst())).isTrue();
        }
    }

    @Test
    void rotatingAccountKeyPairPersistsNewKeyToKeystoreFile(@TempDir Path tempDir) throws Exception {
        Path keystoreFile = tempDir.resolve("acme-account.p12");
        KeyPair oldAccountKeyPair = AcmeTestFixtures.rsaKeyPair();
        seedExpiredAccountKeyPair(keystoreFile, oldAccountKeyPair);
        byte[] macSecret = AcmeTestFixtures.randomBytes(32);

        try (FakeAcmeServer server = new FakeAcmeServer(tempDir.resolve("challenges"))) {
            DsTlsCa ca = approvedDsTlsCa(server.directoryUrl());
            DsTlsAcmeService service = new DsTlsAcmeService(acmePropertiesWithEab(macSecret), acmeConfig(30), acmeClient);

            service.checkAccountKeyPairAndRenewIfNecessary(keystoreFile.toFile(), ca);

            KeyStore reloaded = KeyStore.getInstance("PKCS12");
            try (InputStream in = Files.newInputStream(keystoreFile)) {
                reloaded.load(in, STORE_PASSWORD);
            }
            X509Certificate reloadedCertificate = (X509Certificate) reloaded.getCertificate(DsTlsAcmeService.ACCOUNT_ALIAS);
            PrivateKey reloadedPrivateKey =
                    (PrivateKey) reloaded.getKey(DsTlsAcmeService.ACCOUNT_ALIAS, DsTlsAcmeService.ACCOUNT_ALIAS.toCharArray());

            assertThat(reloadedCertificate).isNotNull();
            assertThat(reloadedCertificate.getPublicKey()).isNotEqualTo(oldAccountKeyPair.getPublic());
            assertKeyPairMatches(reloadedPrivateKey, reloadedCertificate.getPublicKey());
        }
    }

    @Test
    void reusesFixedNonMemberAliasAcrossCalls(@TempDir Path tempDir) throws Exception {
        DsTlsAcmeService service = new DsTlsAcmeService(acmePropertiesWithPassword(), acmeConfig(30), acmeClient);
        DsTlsCa ca = approvedDsTlsCa("http://example.invalid/directory");
        Path keystoreFile = tempDir.resolve("acme-account.p12");

        KeyPair first = service.getAccountKeyPair(keystoreFile.toFile(), ca);
        KeyPair second = service.getAccountKeyPair(keystoreFile.toFile(), ca);

        assertThat(first.getPublic()).isEqualTo(second.getPublic());
    }

    private static void assertKeyPairMatches(PrivateKey privateKey, PublicKey publicKey) throws Exception {
        byte[] challenge = "ds-tls-acme-account-key-rotation-check".getBytes(StandardCharsets.UTF_8);

        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(privateKey);
        signer.update(challenge);
        byte[] signature = signer.sign();

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(publicKey);
        verifier.update(challenge);
        assertThat(verifier.verify(signature)).isTrue();
    }

    private static void seedExpiredAccountKeyPair(Path keystoreFile, KeyPair keyPair) throws Exception {
        X509Certificate expiredWrapperCert = AcmeTestFixtures.selfSignedCertificate(keyPair, DsTlsAcmeService.ACCOUNT_ALIAS,
                Instant.now().minus(400, ChronoUnit.DAYS), Instant.now().minus(1, ChronoUnit.DAYS));
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, STORE_PASSWORD);
        keyStore.setKeyEntry(DsTlsAcmeService.ACCOUNT_ALIAS, keyPair.getPrivate(), DsTlsAcmeService.ACCOUNT_ALIAS.toCharArray(),
                new X509Certificate[] {expiredWrapperCert});
        try (OutputStream out = Files.newOutputStream(keystoreFile)) {
            keyStore.store(out, STORE_PASSWORD);
        }
    }

    private static DsTlsCa approvedDsTlsCa(String directoryUrl) {
        return new DsTlsCa().setName(CA_NAME).setAcmeServerDirectoryUrl(directoryUrl);
    }

    private static AcmeConfig acmeConfig(int renewalTimeBeforeExpirationDate) {
        return new AcmeConfig() {
            @Override
            public boolean isRenewalActive() {
                return true;
            }

            @Override
            public int getRenewalRetryDelay() {
                return 5;
            }

            @Override
            public int getRenewalInterval() {
                return 60;
            }

            @Override
            public int getRenewalTimeBeforeExpirationDate() {
                return renewalTimeBeforeExpirationDate;
            }

            @Override
            public int getKeypairRenewalTimeBeforeExpirationDate() {
                return renewalTimeBeforeExpirationDate;
            }

            @Override
            public int getAuthorizationWaitAttempts() {
                return 10;
            }

            @Override
            public int getAuthorizationWaitInterval() {
                return 1;
            }

            @Override
            public int getCertificateWaitAttempts() {
                return 10;
            }

            @Override
            public int getCertificateWaitInterval() {
                return 1;
            }

            @Override
            public int getCertificateAccountKeyPairExpiration() {
                return 365;
            }

            @Override
            public int getChallengePort() {
                return 8180;
            }

            @Override
            public int getKeyLength() {
                return KEY_LENGTH;
            }
        };
    }

    private static AcmeProperties acmePropertiesWithPassword() {
        AcmeProperties acmeProperties = new AcmeProperties();
        acmeProperties.setAccountKeystorePassword(new String(STORE_PASSWORD));
        return acmeProperties;
    }

    private static AcmeProperties acmePropertiesWithEab(byte[] macSecret) {
        AcmeProperties.Credentials credentials = new AcmeProperties.Credentials();
        credentials.setKid("test-kid");
        credentials.setMacKey(AcmeTestFixtures.encodeBase64Url(macSecret));

        AcmeProperties.CA ca = new AcmeProperties.CA();
        ca.setMacKeyBase64Encoded(true);
        ca.setMembers(Map.of(DsTlsAcmeService.ACCOUNT_ALIAS, credentials));

        AcmeProperties.EabCredentials eabCredentials = new AcmeProperties.EabCredentials();
        eabCredentials.setCertificateAuthorities(Map.of(CA_NAME, ca));

        AcmeProperties acmeProperties = acmePropertiesWithPassword();
        acmeProperties.setEabCredentials(eabCredentials);
        return acmeProperties;
    }
}
