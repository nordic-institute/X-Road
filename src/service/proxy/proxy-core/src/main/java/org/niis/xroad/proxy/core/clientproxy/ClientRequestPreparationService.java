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

import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.SoapUtils;
import ee.ria.xroad.common.util.HttpSender;

import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.protocol.HttpClientContext;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.service.ServiceAddressResolver;
import org.niis.xroad.proxy.core.util.OpMonitoringDataHelper;
import org.niis.xroad.proxy.core.util.ProxyMessageUtils;
import org.niis.xroad.proxy.core.util.ProxyRequestContext;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;

import static ee.ria.xroad.common.Version.XROAD_VERSION;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_HASH_ALGO_ID;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_ORIGINAL_CONTENT_TYPE;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_ORIGINAL_SOAP_ACTION;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_PROXY_VERSION;
import static org.niis.xroad.proxy.core.clientproxy.FastestConnectionSelectingSSLSocketFactory.ID_SELECTED_TARGET;
import static org.niis.xroad.proxy.core.clientproxy.FastestConnectionSelectingSSLSocketFactory.ID_TARGETS;

/**
 * CDI singleton bean that consolidates client-side request preparation logic shared between
 * {@link ClientRestMessageProcessor} and {@link ClientSoapMessageProcessor}.
 *
 * <p>Resolves target addresses, configures the {@link HttpSender} (SSL attributes, timeouts, headers),
 * and provides the dummy service address used for multi-address SSL routing.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class ClientRequestPreparationService {

    private final ServiceAddressResolver serviceAddressResolver;
    private final ProxyProperties proxyProperties;
    private final OpMonitoringDataHelper opMonitoringDataHelper;
    private final UnusableAddressTracker unusableAddressTracker;

    @Getter
    private URI dummyServiceAddress;

    @PostConstruct
    public void init() {
        try {
            dummyServiceAddress = new URI("https", null, "localhost",
                    proxyProperties.serverProxyPort(), "/", null, null);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Unexpected", e);
        }
    }

    /**
     * Prepares an {@link HttpSender} for a client proxy request: resolves target addresses,
     * sets SSL attributes, connection pool user token, timeouts, and common request headers.
     *
     * <p>When {@code originalSoapAction} is non-null (SOAP path), the
     * {@code HEADER_ORIGINAL_SOAP_ACTION} header is also added.
     *
     * @param httpSender         the HTTP sender to configure
     * @param requestServiceId   the service identifier for the outgoing request
     * @param securityServerId   the target security server, or null if not specified
     * @param ctx                the per-request proxy context
     * @param opMonitoringData   operational monitoring data to update, or null if not collected
     * @param originalSoapAction the original SOAP action header value (SOAP path only), or null for REST
     * @return the resolved array of target URIs
     * @throws XrdRuntimeException if no addresses can be resolved
     */
    public URI[] prepareRequest(HttpSender httpSender, ServiceId requestServiceId,
                                SecurityServerId securityServerId, ProxyRequestContext ctx,
                                OpMonitoringData opMonitoringData,
                                @Nullable String originalSoapAction) {
        if (proxyProperties.sslEnabled()) {
            httpSender.setAttribute(AuthTrustVerifier.ID_PROVIDERNAME, requestServiceId);
        }

        var tmp = serviceAddressResolver.resolve(requestServiceId, securityServerId, ctx);
        Collections.shuffle(tmp);
        URI[] addresses = tmp.toArray(new URI[0]);

        opMonitoringDataHelper.updateOpMonitoringServiceSecurityServerAddress(addresses, httpSender, opMonitoringData);

        httpSender.setAttribute(ID_TARGETS, addresses);

        if (proxyProperties.clientProxy().poolEnableConnectionReuse()) {
            httpSender.setAttribute(HttpClientContext.USER_TOKEN, new ProxyMessageUtils.TargetHostsUserToken(addresses));
        }

        httpSender.setConnectionTimeout(proxyProperties.clientProxy().clientProxyTimeout());
        httpSender.setSocketTimeout(proxyProperties.clientProxy().clientHttpclientTimeout());

        httpSender.addHeader(HEADER_HASH_ALGO_ID, SoapUtils.getHashAlgoId().name());
        httpSender.addHeader(HEADER_PROXY_VERSION, XROAD_VERSION);
        httpSender.addHeader(HEADER_ORIGINAL_CONTENT_TYPE, ctx.request().getContentType());

        if (originalSoapAction != null) {
            httpSender.addHeader(HEADER_ORIGINAL_SOAP_ACTION, originalSoapAction);
        }

        return addresses;
    }

    /**
     * Marks the selected target address as unusable if the given exception was caused by a TLS
     * handshake failure. Covers handshake failures that surface only during request execution
     * (e.g. a TLS 1.3 server proxy rejecting the client certificate after the handshake completed).
     *
     * @param httpSender the HTTP sender whose request failed
     * @param exception  the failure to inspect for a TLS handshake error
     */
    public void markAddressUnusableIfHandshakeFailure(HttpSender httpSender, Throwable exception) {
        if (UnusableAddressTracker.isHandshakeFailure(exception)
                && httpSender.getAttribute(ID_SELECTED_TARGET) instanceof URI selectedTarget) {
            unusableAddressTracker.markUnusable(selectedTarget);
        }
    }
}
