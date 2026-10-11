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
import ee.ria.xroad.common.util.RequestWrapper;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.opmonitor.api.QueryOperationalDataResp;
import org.niis.xroad.opmonitor.client.OpMonitorClient;
import org.niis.xroad.proxy.core.addon.opmonitoring.InboundMessageSizeRejection;
import org.niis.xroad.proxy.core.protocol.ProxyMessage;
import org.niis.xroad.proxy.core.serverproxy.AbstractServiceHandler;
import org.niis.xroad.proxy.core.serverproxy.ServiceHandlerResult;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.function.Supplier;

import static ee.ria.xroad.common.ErrorCodes.X_SERVICE_FAILED_X;
import static ee.ria.xroad.common.util.TimeUtils.getEpochMillisecond;
import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_CONTENT_TYPE;
import static org.niis.xroad.common.core.exception.ErrorCode.NETWORK_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.OPERATIONAL_DATA_RESPONSE_TOO_LARGE;
import static org.niis.xroad.common.properties.config.keys.CommonRpcConfigKeys.CHANNEL_OP_MONITOR_MAX_INBOUND_MESSAGE_SIZE;
import static org.niis.xroad.common.properties.config.keys.OpMonitorConfigKeys.MAX_RECORDS_IN_PAYLOAD;
import static org.niis.xroad.opmonitor.api.OpMonitoringRequests.GET_SECURITY_SERVER_HEALTH_DATA;
import static org.niis.xroad.opmonitor.api.OpMonitoringRequests.GET_SECURITY_SERVER_OPERATIONAL_DATA;

/**
 * Service handler for operational monitoring. The monitoring queries are sent to op-monitor over gRPC.
 * The op-monitor client is created on the first query.
 */
@Slf4j
public class OpMonitoringServiceHandlerImpl extends AbstractServiceHandler {

    private final Supplier<OpMonitorClient> opMonitorClientFactory;
    private OpMonitorClient opMonitorClient;

    public OpMonitoringServiceHandlerImpl(ServerConfProvider serverConfProvider,
                                          GlobalConfProvider globalConfProvider,
                                          Supplier<OpMonitorClient> opMonitorClientFactory) {
        super(serverConfProvider, globalConfProvider);
        this.opMonitorClientFactory = opMonitorClientFactory;
    }

    @Override
    public synchronized void destroy() {
        if (opMonitorClient != null) {
            opMonitorClient.close();
            opMonitorClient = null;
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

        try {
            var query = writeQuery(proxyRequestMessage);
            var response = sendQuery(query, proxyRequestMessage.getSoapContentType(), opMonitoringData);

            return new ServiceHandlerResult(response.getContentType(), response.getResponse().newInput());
        } catch (Exception ex) {
            throw XrdRuntimeException.systemException(ex).withPrefix(X_SERVICE_FAILED_X);
        }
    }

    private QueryOperationalDataResp sendQuery(byte[] query, String contentType, OpMonitoringData opMonitoringData) {
        var client = opMonitorClient();

        opMonitoringData.setRequestOutTs(getEpochMillisecond());

        QueryOperationalDataResp response;
        try {
            response = client.queryOperationalData(query, contentType);
        } catch (StatusRuntimeException ex) {
            if (isTransportFailure(ex.getStatus().getCode())) {
                throw networkError(ex, ex.getStatus().toString());
            }
            opMonitoringData.setResponseInTs(getEpochMillisecond());
            var rejection = InboundMessageSizeRejection.from(ex.getStatus());
            if (rejection.isPresent()) {
                throw responseTooLarge(rejection.get(), ex);
            }
            throw ex;
        } catch (XrdRuntimeException ex) {
            if (ex.isCausedBy(NETWORK_ERROR)) {
                throw networkError(ex, ex.getDetails());
            }
            opMonitoringData.setResponseInTs(getEpochMillisecond());
            throw ex;
        }

        opMonitoringData.setResponseInTs(getEpochMillisecond());

        if (response.getContentType().isEmpty()) {
            throw XrdRuntimeException.systemException(INVALID_CONTENT_TYPE, "Could not get content type from response");
        }
        return response;
    }

    private static boolean isTransportFailure(Status.Code code) {
        return code == Status.Code.UNAVAILABLE || code == Status.Code.DEADLINE_EXCEEDED || code == Status.Code.CANCELLED;
    }

    private static XrdRuntimeException networkError(Exception cause, String details) {
        return XrdRuntimeException.systemException(NETWORK_ERROR)
                .cause(cause)
                .details(details)
                .build();
    }

    private static XrdRuntimeException responseTooLarge(InboundMessageSizeRejection rejection, Exception cause) {
        return XrdRuntimeException.systemException(OPERATIONAL_DATA_RESPONSE_TOO_LARGE)
                .cause(cause)
                .details("The op-monitor response of %d bytes exceeds the limit of %d bytes set by %s. Lower %s or raise the limit."
                        .formatted(rejection.size(), rejection.limit(), CHANNEL_OP_MONITOR_MAX_INBOUND_MESSAGE_SIZE.key(),
                                MAX_RECORDS_IN_PAYLOAD.key()))
                .build();
    }

    private static byte[] writeQuery(ProxyMessage proxyRequestMessage) throws IOException {
        var query = new ByteArrayOutputStream();
        proxyRequestMessage.writeSoapContent(query);
        return query.toByteArray();
    }

    private synchronized OpMonitorClient opMonitorClient() {
        if (opMonitorClient == null) {
            opMonitorClient = opMonitorClientFactory.get();
        }
        return opMonitorClient;
    }
}
