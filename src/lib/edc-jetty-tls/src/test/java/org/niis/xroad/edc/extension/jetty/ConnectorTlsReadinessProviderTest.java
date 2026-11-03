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
import ee.ria.xroad.common.conf.InternalSSLKey;

import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.web.spi.configuration.PortMapping;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyStore;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConnectorTlsReadinessProviderTest {

    private final MutableClock clock = new MutableClock(Instant.now());
    private XRoadJettyService jettyService;
    private ConnectorTlsReadinessProvider provider;

    @BeforeEach
    void setUp() {
        var registry = new XRoadPortMappingRegistry();
        registry.register(new PortMapping("svc", TestPortUtils.findRandomPort(), "/api"));
        jettyService = new XRoadJettyService(new Monitor() {
        }, registry, clock);
        provider = new ConnectorTlsReadinessProvider(jettyService);
    }

    @AfterEach
    void tearDown() {
        jettyService.shutdown();
    }

    @Test
    void isUnhealthyBeforeMaterialArrivesAndHealthyAfter() throws Exception {
        jettyService.start();

        assertThat(provider.get().succeeded()).isFalse();
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
        clock.set(served.getNotAfter().toInstant().minus(Duration.ofDays(1)));
        jettyService.start();
        jettyService.applyConnectorTlsMaterial(keyStoreOf(TestCertUtil.getInternalKey()));
        var rejectedAt = clock.instant();

        assertThatThrownBy(() -> jettyService.applyConnectorTlsMaterial(unusableKeyStore()))
                .isInstanceOf(ConnectorTlsMaterialRejectedException.class);
        assertThat(provider.get().succeeded()).isTrue();
        assertThat(provider.readinessDetail())
                .containsEntry(ConnectorTlsReadinessProvider.DETAIL_SERVED_EXPIRES, served.getNotAfter().toInstant().toString())
                .containsEntry(ConnectorTlsReadinessProvider.DETAIL_REJECTED_SINCE, rejectedAt.toString())
                .hasEntrySatisfying(ConnectorTlsReadinessProvider.DETAIL_REJECTED_CAUSE, cause -> assertThat(cause).isNotBlank());
    }

    @Test
    void goesDownOnceTheServedCertificateExpiresWhileNewerMaterialIsRejected() throws Exception {
        var served = TestCertUtil.getInternalKey().certChain[0];
        clock.set(served.getNotAfter().toInstant().minus(Duration.ofDays(1)));
        jettyService.start();
        jettyService.applyConnectorTlsMaterial(keyStoreOf(TestCertUtil.getInternalKey()));

        assertThatThrownBy(() -> jettyService.applyConnectorTlsMaterial(unusableKeyStore()))
                .isInstanceOf(ConnectorTlsMaterialRejectedException.class);
        assertThat(provider.get().succeeded()).isTrue();

        clock.set(served.getNotAfter().toInstant());

        assertThat(provider.get().succeeded()).isFalse();
        assertThat(provider.get().getFailureDetail()).contains("expired").contains("rejected since");
    }

    @Test
    void reportsNoDetailWhenNothingIsRejected() throws Exception {
        jettyService.start();
        jettyService.applyConnectorTlsMaterial(keyStoreOf(TestCertUtil.getInternalKey()));

        assertThat(provider.get().succeeded()).isTrue();
        assertThat(provider.readinessDetail()).isEmpty();
    }

    private static KeyStore unusableKeyStore() throws Exception {
        var credentials = TestCertUtil.getOcspSigner();
        var keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry(InternalSSLKey.KEY_ALIAS, credentials.key, "not-the-keystore-password".toCharArray(),
                credentials.certChain);
        return keyStore;
    }
    private static KeyStore keyStoreOf(TestCertUtil.PKCS12 credentials) throws Exception {
        var keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry(InternalSSLKey.KEY_ALIAS, credentials.key, InternalSSLKey.getKEY_PASSWORD(), credentials.certChain);
        return keyStore;
    }
}
