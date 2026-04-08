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

package org.niis.xroad.proxy.core.util;

import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.ResponseWrapper;

import org.junit.jupiter.api.Test;
import org.niis.xroad.opmonitor.api.OpMonitoringData;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ProxyRequestContextTest {

    @Test
    void restRequestContextImplementsProxyRequestContext() {
        var request = mock(RequestWrapper.class);
        var response = mock(ResponseWrapper.class);
        var ctx = new RestRequestContext(request, response, mock(OpMonitoringData.class), null);

        assertThat(ctx).isInstanceOf(ProxyRequestContext.class);
    }

    @Test
    void clientSoapRequestContextImplementsProxyRequestContext() {
        var request = mock(RequestWrapper.class);
        var response = mock(ResponseWrapper.class);
        var ctx = new ClientSoapRequestContext(request, response, mock(OpMonitoringData.class),
                new java.util.concurrent.CountDownLatch(1),
                new java.util.concurrent.CountDownLatch(1),
                new PipedInputStream(), new PipedOutputStream());

        assertThat(ctx).isInstanceOf(ProxyRequestContext.class);
    }

    @Test
    void serverSoapRequestContextImplementsProxyRequestContext() {
        var request = mock(RequestWrapper.class);
        var response = mock(ResponseWrapper.class);
        var ctx = new ServerSoapRequestContext(request, response, mock(OpMonitoringData.class));

        assertThat(ctx).isInstanceOf(ProxyRequestContext.class);
    }

    @Test
    void restRequestContextAccessorsReturnConstructorArgs() {
        var request = mock(RequestWrapper.class);
        var response = mock(ResponseWrapper.class);
        var opMonitoringData = mock(OpMonitoringData.class);
        var targetAddress = "https://example.com";

        var ctx = new RestRequestContext(request, response, opMonitoringData, targetAddress);

        assertThat(ctx.request()).isSameAs(request);
        assertThat(ctx.response()).isSameAs(response);
        assertThat(ctx.opMonitoringData()).isSameAs(opMonitoringData);
        assertThat(ctx.targetAddress()).isEqualTo(targetAddress);
    }

    @Test
    void clientSoapRequestContextCarriesDistinctCountDownLatchesWithCountOne() {
        var request = mock(RequestWrapper.class);
        var response = mock(ResponseWrapper.class);
        var requestHandlerGate = new java.util.concurrent.CountDownLatch(1);
        var httpSenderGate = new java.util.concurrent.CountDownLatch(1);

        var ctx = new ClientSoapRequestContext(request, response, mock(OpMonitoringData.class),
                requestHandlerGate, httpSenderGate,
                new PipedInputStream(), new PipedOutputStream());

        assertThat(ctx.requestHandlerGate()).isSameAs(requestHandlerGate);
        assertThat(ctx.httpSenderGate()).isSameAs(httpSenderGate);
        assertThat(ctx.requestHandlerGate().getCount()).isEqualTo(1L);
        assertThat(ctx.httpSenderGate().getCount()).isEqualTo(1L);
        assertThat(ctx.requestHandlerGate()).isNotSameAs(ctx.httpSenderGate());
    }

    @Test
    void restRequestContextWithNullTargetAddressReturnsNull() {
        var request = mock(RequestWrapper.class);
        var response = mock(ResponseWrapper.class);
        var ctx = new RestRequestContext(request, response, mock(OpMonitoringData.class), null);

        assertThat(ctx.targetAddress()).isNull();
    }

    @Test
    void patternMatchingSwitchOverProxyRequestContextIsExhaustive() {
        var request = mock(RequestWrapper.class);
        var response = mock(ResponseWrapper.class);
        var opMonitoringData = mock(OpMonitoringData.class);

        ProxyRequestContext restCtx = new RestRequestContext(request, response, opMonitoringData, null);
        ProxyRequestContext clientSoapCtx = new ClientSoapRequestContext(request, response, opMonitoringData,
                new java.util.concurrent.CountDownLatch(1),
                new java.util.concurrent.CountDownLatch(1),
                new PipedInputStream(), new PipedOutputStream());
        ProxyRequestContext serverSoapCtx = new ServerSoapRequestContext(request, response, opMonitoringData);

        var restResult = describeContext(restCtx);
        var clientSoapResult = describeContext(clientSoapCtx);
        var serverSoapResult = describeContext(serverSoapCtx);

        assertThat(restResult).isEqualTo("rest");
        assertThat(clientSoapResult).isEqualTo("client-soap");
        assertThat(serverSoapResult).isEqualTo("server-soap");
    }

    private String describeContext(ProxyRequestContext ctx) {
        return switch (ctx) {
            case RestRequestContext ignored -> "rest";
            case ClientSoapRequestContext ignored -> "client-soap";
            case ServerSoapRequestContext ignored -> "server-soap";
        };
    }
}
