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
package ee.ria.xroad.proxy.serverproxy;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.opmonitoring.OpMonitoringDaemonEndpoints;
import ee.ria.xroad.common.opmonitoring.OpMonitoringData;
import ee.ria.xroad.common.opmonitoring.OpMonitoringSystemProperties;
import ee.ria.xroad.common.util.AbstractHttpSender;
import ee.ria.xroad.common.util.HttpSender;
import ee.ria.xroad.common.util.MimeUtils;
import ee.ria.xroad.common.util.TimeUtils;
import ee.ria.xroad.proxy.protocol.ProxyMessage;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpClient;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_SERVICE_FAILED_X;
import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.opmonitoring.OpMonitoringRequests.GET_SECURITY_SERVER_HEALTH_DATA;
import static ee.ria.xroad.common.opmonitoring.OpMonitoringRequests.GET_SECURITY_SERVER_OPERATIONAL_DATA;
import static ee.ria.xroad.common.util.TimeUtils.getEpochMillisecond;

/**
 * Service handler for operational monitoring.
 */
@Slf4j
public class OpMonitoringServiceHandlerImpl implements ServiceHandler {

    private static final int CONNECTION_TIMEOUT_MILLISECONDS = TimeUtils.secondsToMillis(
            OpMonitoringSystemProperties.getOpMonitorServiceConnectionTimeoutSeconds());

    private static final int SOCKET_TIMEOUT_MILLISECONDS = TimeUtils.secondsToMillis(
            OpMonitoringSystemProperties.getOpMonitorServiceSocketTimeoutSeconds());

    private static final String OP_MONITOR_ADDRESS = getOpMonitorAddress();

    private HttpSender sender;

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
        switch (requestServiceId.getServiceCode()) {
            case GET_SECURITY_SERVER_HEALTH_DATA: // $FALL-THROUGH$
            case GET_SECURITY_SERVER_OPERATIONAL_DATA:
                return requestServiceId.getClientId().equals(ServerConf.getIdentifier().getOwner());
            default:
                return false;
        }
    }

    @Override
    public void startHandling(HttpServletRequest servletRequest, ProxyMessage proxyRequestMessage,
            HttpClient opMonitorClient, OpMonitoringData opMonitoringData) throws Exception {
        log.trace("startHandling({})", proxyRequestMessage.getSoap().getService());

        sender = createHttpSender(opMonitorClient);
        sender.setConnectionTimeout(CONNECTION_TIMEOUT_MILLISECONDS);
        sender.setSocketTimeout(SOCKET_TIMEOUT_MILLISECONDS);
        sender.addHeader("accept-encoding", "");

        sendRequest(servletRequest, proxyRequestMessage, opMonitoringData);
    }

    @Override
    public void finishHandling() throws Exception {
        sender.close();
        sender = null;
    }

    @Override
    public String getResponseContentType() {
        return sender.getResponseContentType();
    }

    @Override
    public InputStream getResponseContent() throws Exception {
        return sender.getResponseContent();
    }

    private static HttpSender createHttpSender(HttpClient opMonitorClient) {
        return new HttpSender(opMonitorClient);
    }

    private void sendRequest(HttpServletRequest servletRequest, ProxyMessage proxyRequestMessage,
            OpMonitoringData opMonitoringData) throws Exception {
        log.trace("sendRequest {}", OP_MONITOR_ADDRESS);

        URI opMonitorUri;

        try {
            opMonitorUri = getOpMonitorUri();
        } catch (URISyntaxException e) {
            log.error("Malformed operational monitoring daemon address '{}'", OP_MONITOR_ADDRESS, e);

            throw new CodedException(X_INTERNAL_ERROR, "Malformed operational monitoring daemon address");
        }

        log.info("Sending request to {}", opMonitorUri);

        try (InputStream in = proxyRequestMessage.getSoapContent()) {
            opMonitoringData.setRequestOutTs(getEpochMillisecond());

            sender.doPost(opMonitorUri, in, AbstractHttpSender.CHUNKED_LENGTH,
                    servletRequest.getHeader(MimeUtils.HEADER_ORIGINAL_CONTENT_TYPE));

            opMonitoringData.setResponseInTs(getEpochMillisecond());
        } catch (Exception ex) {
            if (ex instanceof CodedException) {
                opMonitoringData.setResponseInTs(getEpochMillisecond());
            }

            throw translateException(ex).withPrefix(X_SERVICE_FAILED_X);
        }
    }

    private static String getOpMonitorAddress() {
        return String.format("%s://%s:%s%s", OpMonitoringSystemProperties.getOpMonitorDaemonScheme(),
                OpMonitoringSystemProperties.getOpMonitorHost(), OpMonitoringSystemProperties.getOpMonitorPort(),
                OpMonitoringDaemonEndpoints.QUERY_DATA_PATH);
    }

    private static URI getOpMonitorUri() throws URISyntaxException {
        return new URI(OP_MONITOR_ADDRESS);
    }
}
