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

import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.RestRequest;
import ee.ria.xroad.common.util.RequestWrapper;

import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.protocol.HttpContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.niis.xroad.common.properties.CommonProperties;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.proxy.core.protocol.ProxyMessage;
import org.niis.xroad.proxy.core.protocol.ProxyMessageEncoder;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultRestServiceHandlerImplTest {

    private static final int SERVICE_TIMEOUT_SECONDS = 45;
    private static final int SERVICE_TIMEOUT_MILLIS = 45_000;

    @Test
    void shouldApplyServiceTimeoutToAllRequestTimeouts() throws Exception {
        ServiceId serviceId = ServiceId.Conf.create("EE", "GOV", "1234", null, "getRandom");

        ServerConfProvider serverConfProvider = mock(ServerConfProvider.class);
        when(serverConfProvider.getServiceAddress(serviceId)).thenReturn("http://backend.example");
        when(serverConfProvider.getServiceTimeout(serviceId)).thenReturn(SERVICE_TIMEOUT_SECONDS);

        RestRequest rest = mock(RestRequest.class);
        when(rest.getServiceId()).thenReturn(serviceId);
        when(rest.getVerb()).thenReturn(RestRequest.Verb.GET);
        when(rest.getServicePath()).thenReturn("/random");
        when(rest.getHeaders()).thenReturn(List.of());
        ProxyMessage requestMessage = mock(ProxyMessage.class);
        when(requestMessage.getRest()).thenReturn(rest);

        // Stop right after the request is sent: only the outgoing request is checked here
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.execute(any(HttpUriRequest.class), any(HttpContext.class)))
                .thenThrow(new IOException("backend not called in this test"));

        var handler = new DefaultRestServiceHandlerImpl(serverConfProvider, httpClient, mock(CommonProperties.class));

        assertThatThrownBy(() -> handler.startHandling(mock(RequestWrapper.class), requestMessage,
                mock(ProxyMessageEncoder.class), mock(OpMonitoringData.class)))
                .isInstanceOf(IOException.class);

        ArgumentCaptor<HttpUriRequest> sent = ArgumentCaptor.forClass(HttpUriRequest.class);
        verify(httpClient).execute(sent.capture(), any(HttpContext.class));
        RequestConfig config = ((HttpRequestBase) sent.getValue()).getConfig();

        // The request config replaces the client's defaults, so a timeout left unset here would be unbounded
        assertThat(config.getConnectTimeout()).isEqualTo(SERVICE_TIMEOUT_MILLIS);
        assertThat(config.getConnectionRequestTimeout()).isEqualTo(SERVICE_TIMEOUT_MILLIS);
        assertThat(config.getSocketTimeout()).isEqualTo(SERVICE_TIMEOUT_MILLIS);
    }
}
