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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.keys.ProxyConfigKeys;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.opmonitor.api.OperationalDataRecordProto;
import org.niis.xroad.proxy.core.addon.opmonitoring.OpMonitoringBufferImpl;
import org.niis.xroad.proxy.core.addon.opmonitoring.OperationalDataStoreClient;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Operational monitoring runs beside message processing: a failing, hanging or stopped op-monitor connection
 * must not change how the server proxy handles requests.
 */
class ServerProxyHandlerOpMonitoringFailureTest {
    private static final int REQUESTS = 50;

    private final ServerSoapMessageProcessor soapMessageProcessor = mock(ServerSoapMessageProcessor.class);
    private final CountDownLatch opMonitorHang = new CountDownLatch(1);
    private OpMonitoringBufferImpl opMonitoringBuffer;

    @AfterEach
    void tearDown() {
        opMonitorHang.countDown();
        opMonitoringBuffer.destroy();
    }

    @Test
    void requestsAreHandledWhenEveryStoreToOpMonitorFails() throws Exception {
        var handler = handlerWithOpMonitorClient(records -> {
            throw XrdRuntimeException.systemInternalError("op-monitor unavailable");
        });

        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> handleRequests(handler));

        verify(soapMessageProcessor, times(REQUESTS)).process(any());
    }

    @Test
    void requestsAreNotBlockedWhileOpMonitorHangs() throws Exception {
        var handler = handlerWithOpMonitorClient(records -> {
            opMonitorHang.await();
            return OperationalDataStoreClient.Result.STORED;
        });

        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> handleRequests(handler));

        verify(soapMessageProcessor, times(REQUESTS)).process(any());
    }

    @Test
    void requestsAreNotBlockedWhenOpMonitorReportsTooLarge() throws Exception {
        var handler = handlerWithOpMonitorClient(records -> OperationalDataStoreClient.Result.TOO_LARGE);

        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> handleRequests(handler));

        verify(soapMessageProcessor, times(REQUESTS)).process(any());
    }

    @Test
    void requestIsHandledAfterTheMonitoringBufferWasShutDown() throws Exception {
        var handler = handlerWithOpMonitorClient(records -> OperationalDataStoreClient.Result.STORED);
        opMonitoringBuffer.destroy();

        handleRequests(handler);

        verify(soapMessageProcessor, times(REQUESTS)).process(any());
    }

    private ServerProxyHandler handlerWithOpMonitorClient(InterruptibleStoreClient storeClient) {
        var properties = new ProxyProperties(XRoadConfigBuilder.create().register(ProxyConfigKeys.instance())
                .overrides(Map.of("xroad.proxy.addon.op-monitor.enabled", "true",
                        "xroad.proxy.addon.op-monitor.buffer.size", "10"))
                .build());
        opMonitoringBuffer = new OpMonitoringBufferImpl(mock(ServerConfProvider.class), properties.addon().opMonitor(),
                () -> records -> {
                    try {
                        return storeClient.store(records);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw XrdRuntimeException.systemInternalError("interrupted");
                    }
                });
        opMonitoringBuffer.init();
        return new ServerProxyHandler(mock(ServerRestMessageProcessor.class), soapMessageProcessor,
                mock(ProxyProperties.ServerProperties.class), mock(ClientProxyVersionVerifier.class),
                mock(GlobalConfProvider.class), opMonitoringBuffer);
    }

    private void handleRequests(ServerProxyHandler handler) throws Exception {
        for (int i = 0; i < REQUESTS; i++) {
            var callback = mock(Callback.class);

            assertThat(handler.handle(request(), response(), callback)).isTrue();

            verify(callback).succeeded();
        }
    }

    private static Request request() {
        var request = mock(Request.class);
        when(request.getConnectionMetaData()).thenReturn(mock(ConnectionMetaData.class));
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeaders()).thenReturn(HttpFields.build());
        return request;
    }

    private static Response response() {
        var response = mock(Response.class);
        when(response.getHeaders()).thenReturn(mock(HttpFields.Mutable.class));
        return response;
    }

    @FunctionalInterface
    private interface InterruptibleStoreClient {
        OperationalDataStoreClient.Result store(List<OperationalDataRecordProto> records)
                throws InterruptedException;
    }
}
