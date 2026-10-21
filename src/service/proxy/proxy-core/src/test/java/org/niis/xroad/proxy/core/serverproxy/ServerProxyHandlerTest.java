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
package org.niis.xroad.proxy.core.serverproxy;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.server.ConnectionMetaData;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.opmonitor.client.OpMonitorClient;
import org.niis.xroad.proxy.core.addon.opmonitoring.NoOpMonitoringBuffer;
import org.niis.xroad.proxy.core.addon.opmonitoring.serverproxy.OpMonitoringServiceHandlerImpl;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.protocol.ProxyMessage;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.niis.xroad.opmonitor.api.OpMonitoringData.SecurityServerType.PRODUCER;
import static org.niis.xroad.opmonitor.api.OpMonitoringRequests.GET_SECURITY_SERVER_HEALTH_DATA;
import static org.niis.xroad.opmonitor.api.OpMonitoringRequests.GET_SECURITY_SERVER_OPERATIONAL_DATA;

public class ServerProxyHandlerTest {

    @Test
    public void shouldExecuteClientProxyVersionCheck() throws Exception {
        final var request = getMockedRequest();
        final var callback = mock(Callback.class);
        var globalConfProvider = mock(GlobalConfProvider.class);
        var checkMock = mock(ClientProxyVersionVerifier.class);

        var serverRestMessageProcessor = mock(ServerRestMessageProcessor.class);
        var serverSoapMessageProcessor = mock(ServerSoapMessageProcessor.class);

        ServerProxyHandler serverProxyHandler = new ServerProxyHandler(serverRestMessageProcessor,
                serverSoapMessageProcessor,
                mock(ProxyProperties.ServerProperties.class),
                checkMock, globalConfProvider, new NoOpMonitoringBuffer());

        serverProxyHandler.handle(request, getMockedResponse(), callback);

        verify(checkMock).check(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {GET_SECURITY_SERVER_HEALTH_DATA, GET_SECURITY_SERVER_OPERATIONAL_DATA})
    public void shouldAnswerOpMonitorQueryWithFaultWithoutReachingOpMonitorWhenGlobalConfigurationIsInvalid(String serviceCode)
            throws Exception {
        final var request = getMockedRequest();
        when(request.getHeaders()).thenReturn(HttpFields.build());
        final var response = getMockedResponse();
        final var callback = mock(Callback.class);
        var globalConfProvider = mock(GlobalConfProvider.class);
        doThrow(XrdRuntimeException.systemInternalError("Global configuration is expired"))
                .when(globalConfProvider).verifyValidity();
        var opMonitorClient = mock(OpMonitorClient.class);
        var opMonitoringServiceHandler = new OpMonitoringServiceHandlerImpl(mock(ServerConfProvider.class),
                globalConfProvider, () -> opMonitorClient);
        var opMonitorQuery = mock(ProxyMessage.class, RETURNS_DEEP_STUBS);
        when(opMonitorQuery.getSoap().getService().getServiceCode()).thenReturn(serviceCode);
        when(opMonitorQuery.getSoap().getClient()).thenReturn(null);
        var serverSoapMessageProcessor = mock(ServerSoapMessageProcessor.class);
        when(serverSoapMessageProcessor.process(any())).thenAnswer(invocation ->
                opMonitoringServiceHandler.startHandling(null, opMonitorQuery, new OpMonitoringData(PRODUCER, 0L)) != null);

        ServerProxyHandler serverProxyHandler = new ServerProxyHandler(mock(ServerRestMessageProcessor.class),
                serverSoapMessageProcessor,
                mock(ProxyProperties.ServerProperties.class),
                mock(ClientProxyVersionVerifier.class), globalConfProvider, new NoOpMonitoringBuffer());

        serverProxyHandler.handle(request, response, callback);

        var body = ArgumentCaptor.forClass(ByteBuffer.class);
        verify(response).write(any(Boolean.class), body.capture(), any());
        assertThat(StandardCharsets.UTF_8.decode(body.getValue()).toString()).contains("<faultcode>");
        verifyNoInteractions(opMonitorClient);
    }

    private Request getMockedRequest() {
        final var request = mock(Request.class);
        final var connectionMetaData = mock(ConnectionMetaData.class);
        when(request.getConnectionMetaData()).thenReturn(connectionMetaData);

        when(request.getMethod()).thenReturn("POST");
        return request;
    }

    private Response getMockedResponse() {
        final var response = mock(Response.class);
        final var headers = mock(HttpFields.Mutable.class);
        when(response.getHeaders()).thenReturn(headers);

        return response;
    }
}
