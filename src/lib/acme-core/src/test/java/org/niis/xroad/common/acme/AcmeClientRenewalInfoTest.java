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

import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.common.acme.testsupport.AcmeTestFixtures.rsaKeyPair;
import static org.niis.xroad.common.acme.testsupport.AcmeTestFixtures.selfSignedCertificate;

/**
 * ACME Renewal Information (ARI) renewal decisions: does the CA offer ARI, is renewal due per its suggested window,
 * and the fallback to a fixed expiry offset when the CA offers no ARI at all. None of this is keyed on a member
 * identifier or a signer key-usage type - only on the account and the certificate being considered for renewal.
 * <p>
 * {@link AcmeClient#login} never routes through the {@code xrd-acme}/{@code xrd-acme-profile-id} custom scheme
 * (unlike {@link AcmeClient#orderCertificate}), so every call in this class exercises the plain generic acme4j
 * provider instead - a second, distinct path through the service-loader resolution.
 */
class AcmeClientRenewalInfoTest {

    private static final int FALLBACK_DAYS_BEFORE_EXPIRATION = 30;

    private final AcmeClient acmeClient = new AcmeClient();

    @Test
    void hasRenewalInfoTrueWhenDirectoryAdvertisesIt(@TempDir Path tempDir) throws Exception {
        try (FakeAcmeServer server = new FakeAcmeServer(tempDir)) {
            assertThat(acmeClient.hasRenewalInfo(accountContext(server))).isTrue();
        }
    }

    @Test
    void hasRenewalInfoFalseWhenDirectoryOmitsIt(@TempDir Path tempDir) throws Exception {
        try (FakeAcmeServer server = new FakeAcmeServer(tempDir)) {
            server.disableRenewalInfo();
            assertThat(acmeClient.hasRenewalInfo(accountContext(server))).isFalse();
        }
    }

    @Test
    void renewalRequiredWhenSuggestedWindowIsInThePast(@TempDir Path tempDir) throws Exception {
        try (FakeAcmeServer server = new FakeAcmeServer(tempDir)) {
            server.setRenewalWindow(Instant.now().minus(2, ChronoUnit.DAYS), Instant.now().minus(1, ChronoUnit.DAYS));
            X509Certificate certificate = anyCertificate();

            assertThat(acmeClient.isRenewalRequired(accountContext(server), certificate)).isTrue();
        }
    }

    @Test
    void renewalNotRequiredWhenSuggestedWindowIsInTheFuture(@TempDir Path tempDir) throws Exception {
        try (FakeAcmeServer server = new FakeAcmeServer(tempDir)) {
            server.setRenewalWindow(Instant.now().plus(30, ChronoUnit.DAYS), Instant.now().plus(31, ChronoUnit.DAYS));
            X509Certificate certificate = anyCertificate();

            assertThat(acmeClient.isRenewalRequired(accountContext(server), certificate)).isFalse();
        }
    }

    @Test
    void nextRenewalTimeUsesSuggestedWindowStartWhenAriIsAvailable(@TempDir Path tempDir) throws Exception {
        try (FakeAcmeServer server = new FakeAcmeServer(tempDir)) {
            Instant windowStart = Instant.now().plus(45, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
            server.setRenewalWindow(windowStart, windowStart.plus(1, ChronoUnit.DAYS));
            X509Certificate certificate = anyCertificate();

            Instant nextRenewalTime = acmeClient.getNextRenewalTime(accountContext(server), certificate, FALLBACK_DAYS_BEFORE_EXPIRATION);

            assertThat(nextRenewalTime).isEqualTo(windowStart);
        }
    }

    @Test
    void nextRenewalTimeFallsBackToFixedOffsetWhenAriIsUnavailable(@TempDir Path tempDir) throws Exception {
        try (FakeAcmeServer server = new FakeAcmeServer(tempDir)) {
            server.disableRenewalInfo();
            Instant notAfter = Instant.now().plus(60, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
            X509Certificate certificate = selfSignedCertificate(rsaKeyPair(), "ss-fallback.example",
                    Instant.now().minus(1, ChronoUnit.DAYS), notAfter);

            Instant nextRenewalTime = acmeClient.getNextRenewalTime(accountContext(server), certificate, FALLBACK_DAYS_BEFORE_EXPIRATION);

            assertThat(nextRenewalTime).isEqualTo(notAfter.minus(FALLBACK_DAYS_BEFORE_EXPIRATION, ChronoUnit.DAYS));
        }
    }

    private static AcmeAccountContext accountContext(FakeAcmeServer server) throws Exception {
        return new AcmeAccountContext(server.directoryUrl(), rsaKeyPair(), null, null, null);
    }

    private static X509Certificate anyCertificate() throws Exception {
        return selfSignedCertificate(rsaKeyPair(), "ss-ari.example",
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(90, ChronoUnit.DAYS));
    }
}
