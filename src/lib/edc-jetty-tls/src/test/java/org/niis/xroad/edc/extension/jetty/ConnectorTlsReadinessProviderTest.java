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
package org.niis.xroad.edc.extension.jetty;

import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.TestPortUtils;

import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.web.spi.configuration.PortMapping;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.edc.reload.MaterialRejectedException;
import org.niis.xroad.test.MutableClock;

import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.niis.xroad.edc.extension.jetty.ConnectorTlsTestSupport.keyStoreOf;
import static org.niis.xroad.edc.extension.jetty.ConnectorTlsTestSupport.wrongKeyPassword;

class ConnectorTlsReadinessProviderTest {

    private final MutableClock clock = new MutableClock(Instant.now());
    private XRoadJettyService jettyService;
    private ConnectorTlsReadinessProvider provider;
    private int port;

    @BeforeEach
    void setUp() {
        var registry = new XRoadPortMappingRegistry();
        port = TestPortUtils.findRandomPort();
        registry.register(new PortMapping("svc", port, "/api"));
        jettyService = new XRoadJettyService(new Monitor() {
        }, registry, clock);
        provider = new ConnectorTlsReadinessProvider(jettyService);
    }

    @AfterEach
    void tearDown() {
        jettyService.shutdown();
    }

    private void setClock(Instant instant) {
        clock.advance(Duration.between(clock.instant(), instant));
    }

    @Test
    void isUnhealthyBeforeMaterialArrivesAndHealthyAfter() throws Exception {
        jettyService.start();

        assertThat(provider.get().succeeded()).isFalse();
        assertThat(provider.get().getFailureDetail()).contains("not served yet");
        assertThat(provider.get().getComponent()).isEqualTo(ConnectorTlsReadinessProvider.COMPONENT);

        jettyService.applyConnectorTlsMaterial(keyStoreOf(TestCertUtil.getInternalKey()));

        assertThat(provider.get().succeeded()).isTrue();
    }

    @Test
    void isUnhealthyWhenMaterialIsStoredButTheServerHasNotStarted() throws Exception {
        jettyService.applyConnectorTlsMaterial(keyStoreOf(TestCertUtil.getInternalKey()));

        assertThat(provider.get().succeeded()).isFalse();

        jettyService.start();

        assertThat(provider.get().succeeded()).isTrue();
    }

    @Test
    void isUnhealthyAfterShutdown() throws Exception {
        jettyService.start();
        jettyService.applyConnectorTlsMaterial(keyStoreOf(TestCertUtil.getInternalKey()));
        assertThat(provider.get().succeeded()).isTrue();

        jettyService.shutdown();

        assertThat(provider.get().succeeded()).isFalse();
    }

    @Test
    void staysUpWithDetailWhileNewerMaterialIsRejectedAndTheServedCertificateIsValid() throws Exception {
        var served = TestCertUtil.getInternalKey().certChain[0];
        setClock(served.getNotAfter().toInstant().minus(Duration.ofDays(1)));
        jettyService.start();
        jettyService.applyConnectorTlsMaterial(keyStoreOf(TestCertUtil.getInternalKey()));
        var rejectedAt = clock.instant();

        assertThatThrownBy(() -> jettyService.applyConnectorTlsMaterial(wrongKeyPassword()))
                .isInstanceOf(MaterialRejectedException.class);
        assertThat(provider.get().succeeded()).isTrue();
        assertThat(provider.readinessDetail())
                .containsEntry(ConnectorTlsReadinessProvider.DETAIL_SERVED_EXPIRES, served.getNotAfter().toInstant().toString())
                .containsEntry(ConnectorTlsReadinessProvider.DETAIL_REJECTED_SINCE, rejectedAt.toString())
                .hasEntrySatisfying(ConnectorTlsReadinessProvider.DETAIL_REJECTED_CAUSE, cause -> assertThat(cause).isNotBlank());
    }

    @Test
    void goesDownOnceTheServedCertificateExpiresWhileNewerMaterialIsRejected() throws Exception {
        var served = TestCertUtil.getInternalKey().certChain[0];
        setClock(served.getNotAfter().toInstant().minus(Duration.ofDays(1)));
        jettyService.start();
        jettyService.applyConnectorTlsMaterial(keyStoreOf(TestCertUtil.getInternalKey()));

        assertThatThrownBy(() -> jettyService.applyConnectorTlsMaterial(wrongKeyPassword()))
                .isInstanceOf(MaterialRejectedException.class);
        assertThat(provider.get().succeeded()).isTrue();

        setClock(served.getNotAfter().toInstant());

        assertThat(provider.get().succeeded()).isFalse();
        assertThat(provider.get().getFailureDetail()).contains("expired").contains("rejected since");
    }

    @Test
    void goesDownWithTheExpiryOnceTheServedCertificateExpiresEvenWithNothingRejected() throws Exception {
        var served = TestCertUtil.getInternalKey().certChain[0];
        setClock(served.getNotAfter().toInstant().minus(Duration.ofDays(1)));
        jettyService.start();
        jettyService.applyConnectorTlsMaterial(keyStoreOf(TestCertUtil.getInternalKey()));

        assertThat(provider.get().succeeded()).isTrue();

        setClock(served.getNotAfter().toInstant());

        assertThat(provider.get().succeeded()).isFalse();
        assertThat(provider.get().getFailureDetail())
                .contains("expired at " + served.getNotAfter().toInstant())
                .doesNotContain("rejected");
        assertThat(provider.readinessDetail()).isEmpty();
    }

    @Test
    void goesDownWhenTheCertificateAlreadyExpiredWhenItWasApplied() throws Exception {
        var served = TestCertUtil.getInternalKey().certChain[0];
        setClock(served.getNotAfter().toInstant().plus(Duration.ofDays(1)));
        jettyService.start();

        jettyService.applyConnectorTlsMaterial(keyStoreOf(TestCertUtil.getInternalKey()));

        assertThat(provider.get().succeeded()).isFalse();
        assertThat(provider.get().getFailureDetail()).contains("expired at");
    }

    @Test
    void staysUpWhenTheVaultBecomesUnreachableAfterTheCertificateIsServed() throws Exception {
        setClock(TestCertUtil.getInternalKey().certChain[0].getNotAfter().toInstant().minus(Duration.ofDays(1)));
        jettyService.start();
        jettyService.applyConnectorTlsMaterial(keyStoreOf(TestCertUtil.getInternalKey()));

        jettyService.recordLoadFailure(new ConnectorTlsKeyStoreLoadException("vault down", "OpenBao unreachable: timeout", null));

        assertThat(provider.get().succeeded()).isTrue();
    }

    @Test
    void namesALoaderFailureWhileNothingIsServed() {
        jettyService.start();

        jettyService.recordLoadFailure(new IllegalStateException("loader   exploded\n at somewhere"));

        assertThat(provider.get().succeeded()).isFalse();
        assertThat(provider.get().getFailureDetail()).isEqualTo("Connector TLS is not served: loader exploded at somewhere");
    }

    @Test
    void namesAnEmptySlotWhileNothingIsServed() {
        jettyService.start();

        jettyService.recordLoadFailure(new ConnectorTlsKeyStoreLoadException("No connector TLS certificate found", "slot empty", null));

        assertThat(provider.get().getFailureDetail()).isEqualTo("Connector TLS is not served: slot empty");
    }

    @Test
    void namesAnUnusableKeyStoreWhileNothingIsServed() throws Exception {
        jettyService.start();

        assertThatThrownBy(() -> jettyService.applyConnectorTlsMaterial(wrongKeyPassword()))
                .isInstanceOf(ConnectorTlsKeyStoreLoadException.class);

        assertThat(provider.get().succeeded()).isFalse();
        assertThat(provider.get().getFailureDetail()).startsWith("Connector TLS is not served: keystore unusable: ");
    }

    @Test
    void namesAPortThatCannotBeBoundWhileNothingIsServed() throws Exception {
        try (var occupied = new ServerSocket(port)) {
            jettyService.start();

            assertThatThrownBy(() -> jettyService.applyConnectorTlsMaterial(keyStoreOf(TestCertUtil.getInternalKey())))
                    .isInstanceOf(EdcException.class);
        }

        assertThat(provider.get().succeeded()).isFalse();
        assertThat(provider.get().getFailureDetail()).contains("port " + port).contains("not bindable");
    }

    @Test
    void reportsNoDetailWhenNothingIsRejected() throws Exception {
        jettyService.start();
        jettyService.applyConnectorTlsMaterial(keyStoreOf(TestCertUtil.getInternalKey()));

        assertThat(provider.get().succeeded()).isTrue();
        assertThat(provider.readinessDetail()).isEmpty();
    }
}
