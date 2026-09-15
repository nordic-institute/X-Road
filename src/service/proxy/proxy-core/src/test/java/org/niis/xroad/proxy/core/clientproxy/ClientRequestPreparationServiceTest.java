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
package org.niis.xroad.proxy.core.clientproxy;

import ee.ria.xroad.common.Version;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.SoapUtils;
import ee.ria.xroad.common.util.HttpSender;
import ee.ria.xroad.common.util.RequestWrapper;

import com.google.common.base.Ticker;
import org.apache.http.client.protocol.HttpClientContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.service.ServiceAddressResolver;
import org.niis.xroad.proxy.core.util.OpMonitoringDataHelper;
import org.niis.xroad.proxy.core.util.ProxyMessageUtils;
import org.niis.xroad.proxy.core.util.RestRequestContext;

import javax.net.ssl.SSLHandshakeException;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static ee.ria.xroad.common.util.MimeUtils.HEADER_HASH_ALGO_ID;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_ORIGINAL_CONTENT_TYPE;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_ORIGINAL_SOAP_ACTION;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_PROXY_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.proxy.core.clientproxy.FastestConnectionSelectingSSLSocketFactory.ID_SELECTED_TARGET;
import static org.niis.xroad.proxy.core.clientproxy.FastestConnectionSelectingSSLSocketFactory.ID_TARGETS;

class ClientRequestPreparationServiceTest {

    private static final URI ADDRESS_1 = URI.create("https://ss1.example.org:5500");
    private static final URI ADDRESS_2 = URI.create("https://ss2.example.org:5500");

    private static final ServiceId SERVICE_ID =
            ServiceId.Conf.create("DEV", "COM", "1234", "TestService", "mock1");
    private static final SecurityServerId HINT =
            SecurityServerId.Conf.create("DEV", "COM", "1234", "SS1");
    private static final String CONTENT_TYPE = "application/json";

    private final UnusableAddressTracker tracker =
            new UnusableAddressTracker(Duration.ofSeconds(180), Ticker.systemTicker());
    private final ServiceAddressResolver addressResolver = mock(ServiceAddressResolver.class);
    private final ProxyProperties proxyProperties = mock(ProxyProperties.class);
    private final ProxyProperties.ClientProxyProperties clientProxyProperties =
            mock(ProxyProperties.ClientProxyProperties.class);
    private final OpMonitoringDataHelper opMonitoringDataHelper = mock(OpMonitoringDataHelper.class);
    private final ClientRequestPreparationService service = new ClientRequestPreparationService(
            addressResolver, proxyProperties, opMonitoringDataHelper, tracker);
    private final HttpSender httpSender = mock(HttpSender.class);

    @BeforeEach
    void stubProxyProperties() {
        when(proxyProperties.clientProxy()).thenReturn(clientProxyProperties);
    }

    @Test
    void marksSelectedTargetOnHandshakeFailureWithMultipleAddresses() {
        when(httpSender.getAttribute(ID_TARGETS)).thenReturn(new URI[]{ADDRESS_1, ADDRESS_2});
        when(httpSender.getAttribute(ID_SELECTED_TARGET)).thenReturn(ADDRESS_1);

        service.markAddressUnusableIfHandshakeFailure(httpSender, new SSLHandshakeException("rejected"));

        assertThat(tracker.isUnusable(ADDRESS_1)).isTrue();
        assertThat(tracker.isUnusable(ADDRESS_2)).isFalse();
    }

    @Test
    void doesNotMarkSingleAddress() {
        when(httpSender.getAttribute(ID_TARGETS)).thenReturn(new URI[]{ADDRESS_1});
        when(httpSender.getAttribute(ID_SELECTED_TARGET)).thenReturn(ADDRESS_1);

        service.markAddressUnusableIfHandshakeFailure(httpSender, new SSLHandshakeException("rejected"));

        assertThat(tracker.isUnusable(ADDRESS_1)).isFalse();
    }

    @Test
    void doesNotMarkWhenNoTargetWasSelected() {
        when(httpSender.getAttribute(ID_TARGETS)).thenReturn(new URI[]{ADDRESS_1, ADDRESS_2});
        when(httpSender.getAttribute(ID_SELECTED_TARGET)).thenReturn(null);

        service.markAddressUnusableIfHandshakeFailure(httpSender, new SSLHandshakeException("rejected"));

        assertThat(tracker.isUnusable(ADDRESS_1)).isFalse();
        assertThat(tracker.isUnusable(ADDRESS_2)).isFalse();
    }

    @Test
    void doesNotMarkOnNonHandshakeFailure() {
        when(httpSender.getAttribute(ID_TARGETS)).thenReturn(new URI[]{ADDRESS_1, ADDRESS_2});
        when(httpSender.getAttribute(ID_SELECTED_TARGET)).thenReturn(ADDRESS_1);

        service.markAddressUnusableIfHandshakeFailure(httpSender, new IOException("connection reset"));

        assertThat(tracker.isUnusable(ADDRESS_1)).isFalse();
    }

    @Test
    void prepareRequestGivesTheSenderEveryResolvedAddress() {
        resolverReturns(ADDRESS_1, ADDRESS_2);

        var targets = prepareRequest();

        assertThat(targets).containsExactlyInAnyOrder(ADDRESS_1, ADDRESS_2);
        verify(httpSender).setAttribute(eq(ID_TARGETS), eq(targets));
        verify(httpSender).addHeader(HEADER_HASH_ALGO_ID, SoapUtils.getHashAlgoId().name());
        verify(httpSender).addHeader(HEADER_PROXY_VERSION, Version.XROAD_VERSION);
        verify(httpSender).addHeader(HEADER_ORIGINAL_CONTENT_TYPE, CONTENT_TYPE);
    }

    @Test
    void prepareRequestPassesTheSecurityServerHintToTheResolver() {
        resolverReturns(ADDRESS_1);

        prepareRequest();

        verify(addressResolver).resolve(eq(SERVICE_ID), eq(HINT), any());
    }

    @Test
    void prepareRequestSetsProviderNameForAuthTrustVerificationWhenSslIsEnabled() {
        when(proxyProperties.sslEnabled()).thenReturn(true);
        resolverReturns(ADDRESS_1, ADDRESS_2);

        prepareRequest();

        verify(httpSender).setAttribute(AuthTrustVerifier.ID_PROVIDERNAME, SERVICE_ID);
    }

    @Test
    void prepareRequestOmitsProviderNameWhenSslIsDisabled() {
        when(proxyProperties.sslEnabled()).thenReturn(false);
        resolverReturns(ADDRESS_1, ADDRESS_2);

        prepareRequest();

        verify(httpSender, never()).setAttribute(eq(AuthTrustVerifier.ID_PROVIDERNAME), any());
    }

    @Test
    void prepareRequestBindsPooledConnectionsToTheTargetSetWhenReuseIsEnabled() {
        when(clientProxyProperties.poolEnableConnectionReuse()).thenReturn(true);
        resolverReturns(ADDRESS_1, ADDRESS_2);

        prepareRequest();

        verify(httpSender).setAttribute(eq(HttpClientContext.USER_TOKEN),
                any(ProxyMessageUtils.TargetHostsUserToken.class));
    }

    @Test
    void prepareRequestLeavesConnectionsUnpooledWhenReuseIsDisabled() {
        when(clientProxyProperties.poolEnableConnectionReuse()).thenReturn(false);
        resolverReturns(ADDRESS_1, ADDRESS_2);

        prepareRequest();

        verify(httpSender, never()).setAttribute(eq(HttpClientContext.USER_TOKEN), any());
    }

    @Test
    void prepareRequestAppliesConfiguredTimeouts() {
        when(clientProxyProperties.clientProxyTimeout()).thenReturn(30000);
        when(clientProxyProperties.clientHttpclientTimeout()).thenReturn(60000);
        resolverReturns(ADDRESS_1);

        prepareRequest();

        verify(httpSender).setConnectionTimeout(30000);
        verify(httpSender).setSocketTimeout(60000);
    }

    @Test
    void prepareRequestAddsTheSoapActionHeaderOnlyWhenOneWasGiven() {
        resolverReturns(ADDRESS_1);

        prepareRequest("urn:getRandom");
        verify(httpSender).addHeader(HEADER_ORIGINAL_SOAP_ACTION, "urn:getRandom");

        // The REST path passes null, and an empty SOAPAction header is not the same as none.
        prepareRequest();
        verify(httpSender, never()).addHeader(eq(HEADER_ORIGINAL_SOAP_ACTION), isNull());
    }

    private void resolverReturns(URI... addresses) {
        when(addressResolver.resolve(any(), any(), any())).thenReturn(new ArrayList<>(List.of(addresses)));
    }

    private URI[] prepareRequest() {
        return prepareRequest(null);
    }

    private URI[] prepareRequest(String originalSoapAction) {
        var request = mock(RequestWrapper.class);
        when(request.getContentType()).thenReturn(CONTENT_TYPE);

        var ctx = new RestRequestContext(request, null, null);
        return service.prepareRequest(httpSender, SERVICE_ID, HINT, ctx, null, originalSoapAction);
    }
}
