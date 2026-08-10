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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.niis.xroad.common.acme.testsupport.FakeAcmeServer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.niis.xroad.common.acme.testsupport.AcmeTestFixtures.challengeSettings;
import static org.niis.xroad.common.acme.testsupport.AcmeTestFixtures.encodeBase64Url;
import static org.niis.xroad.common.acme.testsupport.AcmeTestFixtures.generateCsr;
import static org.niis.xroad.common.acme.testsupport.AcmeTestFixtures.randomBytes;
import static org.niis.xroad.common.acme.testsupport.AcmeTestFixtures.rsaKeyPair;

/**
 * Exercises {@link AcmeClient} against {@link FakeAcmeServer}: order placement, EAB handling (including MAC
 * verification), HTTP-01 fulfilment and the custom {@code xrd-acme}/{@code xrd-acme-profile-id} URI schemes. Every
 * order placed here routes through the service-loader-registered {@link AcmeXroadProvider} or
 * {@link AcmeProfileIdProvider} - if the {@code META-INF/services} registration did not move with the code, or no
 * longer resolves, these tests fail with "No ACME provider found" before ever reaching the fake server.
 */
class AcmeClientOrderTest {

    private static final int EAB_SECRET_LENGTH = 32;

    private final AcmeClient acmeClient = new AcmeClient();

    @Test
    void ordersCertificateWithoutEab(@TempDir Path challengeDir) throws Exception {
        try (FakeAcmeServer server = new FakeAcmeServer(challengeDir)) {
            KeyPair accountKeyPair = rsaKeyPair();
            KeyPair domainKeyPair = rsaKeyPair();
            byte[] csr = generateCsr(domainKeyPair, "ss1.example");
            AcmeAccountContext account = new AcmeAccountContext(server.directoryUrl(), accountKeyPair, null, null, null);

            List<X509Certificate> chain = acmeClient.orderCertificate(account, "ss1.example", null, csr, challengeSettings(challengeDir));

            assertThat(chain).hasSize(2);
            assertThat(chain.getFirst().getPublicKey()).isEqualTo(domainKeyPair.getPublic());
            assertThat(chain.getFirst().getSubjectX500Principal().getName()).contains("ss1.example");
            try (var files = Files.list(challengeDir)) {
                assertThat(files).isEmpty();
            }
        }
    }

    @Test
    void ordersCertificateWithCorrectEabMac(@TempDir Path challengeDir) throws Exception {
        try (FakeAcmeServer server = new FakeAcmeServer(challengeDir)) {
            byte[] macSecret = randomBytes(EAB_SECRET_LENGTH);
            server.requireExternalAccountBinding("eab-kid-1", macSecret);
            AcmeEabCredentials eab = new AcmeEabCredentials("eab-kid-1", encodeBase64Url(macSecret), true);
            KeyPair accountKeyPair = rsaKeyPair();
            KeyPair domainKeyPair = rsaKeyPair();
            byte[] csr = generateCsr(domainKeyPair, "ss2.example");
            AcmeAccountContext account = new AcmeAccountContext(server.directoryUrl(), accountKeyPair, () -> eab, null, null);

            List<X509Certificate> chain = acmeClient.orderCertificate(account, "ss2.example", null, csr, challengeSettings(challengeDir));

            assertThat(chain).isNotEmpty();
        }
    }

    @Test
    void rejectsOrderWhenEabMacIsWrong(@TempDir Path challengeDir) throws Exception {
        try (FakeAcmeServer server = new FakeAcmeServer(challengeDir)) {
            server.requireExternalAccountBinding("eab-kid-2", randomBytes(EAB_SECRET_LENGTH));
            AcmeEabCredentials wrongEab = new AcmeEabCredentials("eab-kid-2", encodeBase64Url(randomBytes(EAB_SECRET_LENGTH)), true);
            KeyPair accountKeyPair = rsaKeyPair();
            KeyPair domainKeyPair = rsaKeyPair();
            byte[] csr = generateCsr(domainKeyPair, "ss3.example");
            AcmeAccountContext account = new AcmeAccountContext(server.directoryUrl(), accountKeyPair, () -> wrongEab, null, null);

            assertThatThrownBy(() ->
                    acmeClient.orderCertificate(account, "ss3.example", null, csr, challengeSettings(challengeDir)))
                    .isInstanceOf(AcmeServiceException.class);
        }
    }

    @Test
    void skipsEabWhenCaDoesNotRequireIt(@TempDir Path challengeDir) throws Exception {
        try (FakeAcmeServer server = new FakeAcmeServer(challengeDir)) {
            // EAB credentials are configured on the account, but this CA (unlike the two tests above) never asks
            // for them - the core must not resolve or send them, exactly like today's Let's Encrypt case.
            AcmeEabCredentials eabNeverConsulted = new AcmeEabCredentials("unused", "unused", false);
            KeyPair accountKeyPair = rsaKeyPair();
            KeyPair domainKeyPair = rsaKeyPair();
            byte[] csr = generateCsr(domainKeyPair, "ss3b.example");
            AcmeAccountContext account =
                    new AcmeAccountContext(server.directoryUrl(), accountKeyPair, () -> eabNeverConsulted, null, null);

            List<X509Certificate> chain =
                    acmeClient.orderCertificate(account, "ss3b.example", null, csr, challengeSettings(challengeDir));

            assertThat(chain).isNotEmpty();
        }
    }

    @Test
    void sendsCertificateProfileIdOnlyOnOrderNotOnRenewal(@TempDir Path challengeDir) throws Exception {
        try (FakeAcmeServer server = new FakeAcmeServer(challengeDir)) {
            KeyPair accountKeyPair = rsaKeyPair();
            KeyPair domainKeyPair = rsaKeyPair();
            byte[] csr = generateCsr(domainKeyPair, "ss4.example");
            AcmeAccountContext orderAccount = new AcmeAccountContext(server.directoryUrl(), accountKeyPair, null, null, "xrd-ds-tls");

            List<X509Certificate> chain =
                    acmeClient.orderCertificate(orderAccount, "ss4.example", null, csr, challengeSettings(challengeDir));

            assertThat(server.capturedUserAgents()).anyMatch(ua -> ua.startsWith("profile_id=xrd-ds-tls "));

            server.clearCapturedUserAgents();
            KeyPair newDomainKeyPair = rsaKeyPair();
            byte[] renewalCsr = generateCsr(newDomainKeyPair, "ss4.example");
            List<X509Certificate> renewed = acmeClient.renewCertificate(orderAccount, "ss4.example", chain.getFirst(), renewalCsr,
                    challengeSettings(challengeDir));

            assertThat(renewed).isNotEmpty();
            assertThat(server.capturedUserAgents()).noneMatch(ua -> ua.contains("profile_id="));
        }
    }

    @Test
    void failsOrderWhenHttp01ValidationFails(@TempDir Path challengeDir) throws Exception {
        try (FakeAcmeServer server = new FakeAcmeServer(challengeDir)) {
            server.failNextChallenge();
            KeyPair accountKeyPair = rsaKeyPair();
            KeyPair domainKeyPair = rsaKeyPair();
            byte[] csr = generateCsr(domainKeyPair, "ss5.example");
            AcmeAccountContext account = new AcmeAccountContext(server.directoryUrl(), accountKeyPair, null, null, null);

            assertThatThrownBy(() ->
                    acmeClient.orderCertificate(account, "ss5.example", null, csr, challengeSettings(challengeDir)))
                    .isInstanceOf(AcmeServiceException.class);
        }
    }

    @Test
    void isExternalAccountBindingRequiredReflectsDirectoryMetadata(@TempDir Path challengeDir) throws Exception {
        try (FakeAcmeServer withoutEab = new FakeAcmeServer(challengeDir)) {
            assertThat(acmeClient.isExternalAccountBindingRequired(withoutEab.directoryUrl())).isFalse();
        }
        try (FakeAcmeServer withEab = new FakeAcmeServer(challengeDir)) {
            withEab.requireExternalAccountBinding("some-kid", randomBytes(EAB_SECRET_LENGTH));
            assertThat(acmeClient.isExternalAccountBindingRequired(withEab.directoryUrl())).isTrue();
        }
    }
}
