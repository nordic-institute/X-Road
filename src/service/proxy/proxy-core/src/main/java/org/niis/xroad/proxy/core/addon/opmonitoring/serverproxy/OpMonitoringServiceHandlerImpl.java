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

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.util.HttpSender;
import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.TimeUtils;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpClient;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.opmonitor.api.OpMonitorCommonProperties;
import org.niis.xroad.opmonitor.api.OpMonitoringDaemonEndpoints;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.proxy.core.protocol.ProxyMessage;
import org.niis.xroad.proxy.core.serverproxy.AbstractServiceHandler;
import org.niis.xroad.proxy.core.serverproxy.ProxyMessageSoapEntity;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_SERVICE_FAILED_X;
import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.util.TimeUtils.getEpochMillisecond;
import static org.niis.xroad.opmonitor.api.OpMonitoringRequests.GET_SECURITY_SERVER_HEALTH_DATA;
import static org.niis.xroad.opmonitor.api.OpMonitoringRequests.GET_SECURITY_SERVER_OPERATIONAL_DATA;

/**
 * Service handler for operational monitoring.
 */
@Slf4j

public class OpMonitoringServiceHandlerImpl extends AbstractServiceHandler {

    private final OpMonitorCommonProperties commonProperties;

    private final String opMonitorAddress;

    private final boolean isEnabledPooledConnectionReuse;

    private HttpSender sender;

    public OpMonitoringServiceHandlerImpl(ServerConfProvider serverConfProvider, GlobalConfProvider globalConfProvider,
                                          OpMonitorCommonProperties opMonitorCommonProperties, boolean isEnabledPooledConnectionReuse) {
        super(serverConfProvider, globalConfProvider);
        this.commonProperties = opMonitorCommonProperties;
        this.opMonitorAddress = getOpMonitorAddress();
        this.isEnabledPooledConnectionReuse = isEnabledPooledConnectionReuse;
    }

    @Override
    public boolean shouldVerifyAccess() {
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
    public boolean canHandle(ServiceId requestServiceId,
                             ProxyMessage proxyRequestMessage) {
        return switch (requestServiceId.getServiceCode()) {
            case GET_SECURITY_SERVER_HEALTH_DATA, GET_SECURITY_SERVER_OPERATIONAL_DATA ->
                    requestServiceId.getClientId().equals(serverConfProvider.getIdentifier().getOwner());
            default -> false;
        };
    }

    @Override
    public void startHandling(RequestWrapper servletRequest, ProxyMessage proxyRequestMessage,
                              HttpClient opMonitorClient, OpMonitoringData opMonitoringData) {
        log.trace("startHandling({})", proxyRequestMessage.getSoap().getService());

        sender = createHttpSender(opMonitorClient);
        sender.setConnectionTimeout(TimeUtils.secondsToMillis(commonProperties.service().socketTimeoutSeconds()));
        sender.setSocketTimeout(TimeUtils.secondsToMillis(commonProperties.service().socketTimeoutSeconds()));
        sender.addHeader("accept-encoding", "");

        sendRequest(proxyRequestMessage, opMonitoringData);
    }

    @Override
    public void finishHandling() {
        sender.close();
        sender = null;
    }

    @Override
    public String getResponseContentType() {
        return sender.getResponseContentType();
    }

    @Override
    public InputStream getResponseContent() {
        return sender.getResponseContent();
    }

    private HttpSender createHttpSender(HttpClient opMonitorClient) {
        return new HttpSender(opMonitorClient, isEnabledPooledConnectionReuse);
    }

    private void sendRequest(ProxyMessage proxyRequestMessage, OpMonitoringData opMonitoringData) {
        log.trace("sendRequest {}", opMonitorAddress);

        URI opMonitorUri;

        try {
            opMonitorUri = getOpMonitorUri();
        } catch (URISyntaxException e) {
            log.error("Malformed operational monitoring daemon address '{}'", opMonitorAddress, e);

            throw new CodedException(X_INTERNAL_ERROR, "Malformed operational monitoring daemon address");
        }

        log.info("Sending request to {}", opMonitorUri);

        try {
            opMonitoringData.setRequestOutTs(getEpochMillisecond());

            sender.doPost(opMonitorUri, new ProxyMessageSoapEntity(proxyRequestMessage));

            opMonitoringData.setResponseInTs(getEpochMillisecond());
        } catch (Exception ex) {
            if (ex instanceof CodedException) {
                opMonitoringData.setResponseInTs(getEpochMillisecond());
            }

            throw translateException(ex).withPrefix(X_SERVICE_FAILED_X);
        }
    }

    private String getOpMonitorAddress() {
        return String.format("%s://%s:%s%s",
                commonProperties.connection().scheme(),
                commonProperties.connection().host(),
                commonProperties.connection().port(),
                OpMonitoringDaemonEndpoints.QUERY_DATA_PATH);
    }

    private URI getOpMonitorUri() throws URISyntaxException {
        return new URI(opMonitorAddress);
    }
}
