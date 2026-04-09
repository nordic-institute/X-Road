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
package org.niis.xroad.proxy.core.addon.opmonitoring.serverproxy;

import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.util.HttpSender;
import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.TimeUtils;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.CloseableHttpClient;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.vault.VaultClient;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.opmonitor.api.OpMonitoringDaemonEndpoints;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.proxy.core.addon.opmonitoring.OpMonitoringDaemonHttpClient;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.protocol.ProxyMessage;
import org.niis.xroad.proxy.core.serverproxy.AbstractServiceHandler;
import org.niis.xroad.proxy.core.serverproxy.ProxyMessageSoapEntity;
import org.niis.xroad.proxy.core.serverproxy.ServiceHandlerResult;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.net.URI;
import java.net.URISyntaxException;

import static ee.ria.xroad.common.ErrorCodes.X_SERVICE_FAILED_X;
import static ee.ria.xroad.common.util.TimeUtils.getEpochMillisecond;
import static org.niis.xroad.opmonitor.api.OpMonitoringRequests.GET_SECURITY_SERVER_HEALTH_DATA;
import static org.niis.xroad.opmonitor.api.OpMonitoringRequests.GET_SECURITY_SERVER_OPERATIONAL_DATA;

/**
 * Service handler for operational monitoring.
 * This is a top-level {@link Singleton} CDI singleton — all per-request state is
 * kept in method-local variables and returned via {@link ServiceHandlerResult}.
 * The {@code opMonitorHttpClient} field is set after construction via
 * {@link #setOpMonitorHttpClient(HttpClient)} because it requires SSL keys
 * from {@link ServerConfProvider} which are only available after startup.
 */
@Slf4j
@Singleton
public class OpMonitoringServiceHandlerImpl extends AbstractServiceHandler {

    private final ProxyProperties proxyProperties;
    private final String opMonitorAddress;
    private final VaultClient vaultClient;

    private CloseableHttpClient opMonitorHttpClient;

    public OpMonitoringServiceHandlerImpl(ServerConfProvider serverConfProvider,
                                          GlobalConfProvider globalConfProvider,
                                          VaultClient vaultClient,
                                          ProxyProperties proxyProperties) {
        super(serverConfProvider, globalConfProvider);
        this.proxyProperties = proxyProperties;
        this.vaultClient = vaultClient;
        this.opMonitorAddress = getOpMonitorAddress();
    }

    @PostConstruct
    public void init() {
        try {
            if (proxyProperties.addon().opMonitor().enabled()) {
                opMonitorHttpClient = OpMonitoringDaemonHttpClient.createHttpClient(
                        proxyProperties.addon().opMonitor().connection(), vaultClient,
                        serverConfProvider.getSSLKey());
            }
        } catch (Exception e) {
            throw XrdRuntimeException.systemException(e);
        }
    }

    @PreDestroy
    public void destroy() {
        if (opMonitorHttpClient != null) {
            IOUtils.closeQuietly(opMonitorHttpClient);
        }
    }

    @Override
    public boolean shouldVerifyAccess(ProxyMessage requestMessage) {
        return false;
    }

    @Override
    public boolean shouldVerifySignature() {
        return true;
    }

    @Override
    public boolean shouldLogSignature() {
        return true;
    }

    @Override
    public boolean canHandle(ServiceId requestServiceId, ProxyMessage proxyRequestMessage) {
        return switch (requestServiceId.getServiceCode()) {
            case GET_SECURITY_SERVER_HEALTH_DATA, GET_SECURITY_SERVER_OPERATIONAL_DATA ->
                    requestServiceId.getClientId().equals(serverConfProvider.getIdentifier().getOwner());
            default -> false;
        };
    }

    @Override
    public ServiceHandlerResult startHandling(RequestWrapper servletRequest, ProxyMessage proxyRequestMessage,
                                              OpMonitoringData opMonitoringData) {
        log.trace("startHandling({})", proxyRequestMessage.getSoap().getService());

        var sender = createHttpSender(opMonitorHttpClient);
        var connectionProps = proxyProperties.addon().opMonitor().connection();
        sender.setConnectionTimeout(TimeUtils.secondsToMillis(connectionProps.connectionTimeoutSeconds()));
        sender.setSocketTimeout(TimeUtils.secondsToMillis(connectionProps.socketTimeoutSeconds()));
        sender.addHeader("accept-encoding", "");

        sendRequest(sender, proxyRequestMessage, opMonitoringData);

        return new ServiceHandlerResult(sender.getResponseContentType(), sender.getResponseContent(), sender);
    }

    private HttpSender createHttpSender(HttpClient opMonitorClient) {
        return new HttpSender(opMonitorClient, proxyProperties.clientProxy().poolEnableConnectionReuse());
    }

    private void sendRequest(HttpSender sender, ProxyMessage proxyRequestMessage, OpMonitoringData opMonitoringData) {
        log.trace("sendRequest {}", opMonitorAddress);

        URI opMonitorUri;

        try {
            opMonitorUri = getOpMonitorUri();
        } catch (URISyntaxException e) {
            log.error("Malformed operational monitoring daemon address '{}'", opMonitorAddress, e);

            throw XrdRuntimeException.systemInternalError("Malformed operational monitoring daemon address");
        }

        log.info("Sending request to {}", opMonitorUri);

        try {
            opMonitoringData.setRequestOutTs(getEpochMillisecond());

            sender.doPost(opMonitorUri, new ProxyMessageSoapEntity(proxyRequestMessage));

            opMonitoringData.setResponseInTs(getEpochMillisecond());
        } catch (Exception ex) {
            if (ex instanceof XrdRuntimeException) {
                opMonitoringData.setResponseInTs(getEpochMillisecond());
            }

            throw XrdRuntimeException.systemException(ex).withPrefix(X_SERVICE_FAILED_X);
        }
    }

    private String getOpMonitorAddress() {
        return String.format("%s://%s:%s%s",
                proxyProperties.addon().opMonitor().connection().scheme(),
                proxyProperties.addon().opMonitor().connection().host(),
                proxyProperties.addon().opMonitor().connection().port(),
                OpMonitoringDaemonEndpoints.QUERY_DATA_PATH);
    }

    private URI getOpMonitorUri() throws URISyntaxException {
        return new URI(opMonitorAddress);
    }
}
