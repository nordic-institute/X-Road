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

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ProxyRequestContextTest {

    @Test
    void restRequestContextImplementsProxyRequestContext() {
        var ctx = new RestRequestContext(mock(RequestWrapper.class), mock(ResponseWrapper.class),
                mock(OpMonitoringData.class));

        assertThat(ctx).isInstanceOf(ProxyRequestContext.class);
    }

    @Test
    void clientSoapRequestContextImplementsProxyRequestContext() throws IOException {
        try (var ctx = new ClientSoapRequestContext(mock(RequestWrapper.class), mock(ResponseWrapper.class),
                mock(OpMonitoringData.class))) {
            assertThat(ctx).isInstanceOf(ProxyRequestContext.class);
        }
    }

    @Test
    void serverSoapRequestContextImplementsProxyRequestContext() {
        var ctx = new ServerSoapRequestContext(mock(RequestWrapper.class), mock(ResponseWrapper.class),
                mock(OpMonitoringData.class));

        assertThat(ctx).isInstanceOf(ProxyRequestContext.class);
    }

    @Test
    void restRequestContextAccessorsReturnConstructorArgs() {
        var request = mock(RequestWrapper.class);
        var response = mock(ResponseWrapper.class);
        var opMonitoringData = mock(OpMonitoringData.class);

        var ctx = new RestRequestContext(request, response, opMonitoringData);

        assertThat(ctx.request()).isSameAs(request);
        assertThat(ctx.response()).isSameAs(response);
        assertThat(ctx.opMonitoringData()).isSameAs(opMonitoringData);
    }

    @Test
    void clientSoapRequestContextCreatesDistinctLatchesAndStreams() throws IOException {
        try (var ctx = new ClientSoapRequestContext(mock(RequestWrapper.class), mock(ResponseWrapper.class),
                mock(OpMonitoringData.class))) {
            assertThat(ctx.requestHandlerGate().getCount()).isEqualTo(1L);
            assertThat(ctx.httpSenderGate().getCount()).isEqualTo(1L);
            assertThat(ctx.requestHandlerGate()).isNotSameAs(ctx.httpSenderGate());
            assertThat(ctx.reqIns()).isNotNull();
            assertThat(ctx.reqOuts()).isNotNull();
        }
    }

    @Test
    void patternMatchingSwitchOverProxyRequestContextIsExhaustive() throws IOException {
        var opMonitoringData = mock(OpMonitoringData.class);

        ProxyRequestContext restCtx = new RestRequestContext(mock(RequestWrapper.class),
                mock(ResponseWrapper.class), opMonitoringData);
        try (var clientSoapCtx = new ClientSoapRequestContext(mock(RequestWrapper.class),
                mock(ResponseWrapper.class), opMonitoringData)) {
            ProxyRequestContext serverSoapCtx = new ServerSoapRequestContext(mock(RequestWrapper.class),
                    mock(ResponseWrapper.class), opMonitoringData);

            assertThat(describeContext(restCtx)).isEqualTo("rest");
            assertThat(describeContext(clientSoapCtx)).isEqualTo("client-soap");
            assertThat(describeContext(serverSoapCtx)).isEqualTo("server-soap");
        }
    }

    private String describeContext(ProxyRequestContext ctx) {
        return switch (ctx) {
            case RestRequestContext ignored -> "rest";
            case ClientSoapRequestContext ignored -> "client-soap";
            case ServerSoapRequestContext ignored -> "server-soap";
        };
    }
}
