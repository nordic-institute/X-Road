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

package org.niis.xroad.proxy.core.service;

import ee.ria.xroad.common.util.HttpSender;

import org.apache.http.client.HttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HttpSenderProviderTest {

    private HttpClient clientHttpClient;
    private HttpClient serverHttpClient;
    private ProxyProperties proxyProperties;
    private HttpSenderProvider httpSenderProvider;

    @BeforeEach
    void setUp() {
        clientHttpClient = mock(HttpClient.class);
        serverHttpClient = mock(HttpClient.class);
        proxyProperties = mock(ProxyProperties.class);

        var clientProxy = mock(ProxyProperties.ClientProxyProperties.class);
        when(proxyProperties.clientProxy()).thenReturn(clientProxy);
        when(clientProxy.poolEnableConnectionReuse()).thenReturn(true);

        httpSenderProvider = new HttpSenderProvider(clientHttpClient, serverHttpClient, proxyProperties);
    }

    @Test
    void createClientHttpSenderReturnsNonNull() {
        HttpSender sender = httpSenderProvider.createClientHttpSender();
        assertThat(sender).isNotNull();
    }

    @Test
    void createServerHttpSenderReturnsNonNull() {
        HttpSender sender = httpSenderProvider.createServerHttpSender();
        assertThat(sender).isNotNull();
    }

    @Test
    void createClientHttpSenderReturnsNewInstanceOnEachCall() {
        HttpSender sender1 = httpSenderProvider.createClientHttpSender();
        HttpSender sender2 = httpSenderProvider.createClientHttpSender();
        assertThat(sender1).isNotSameAs(sender2);
    }

    @Test
    void createServerHttpSenderReturnsNewInstanceOnEachCall() {
        HttpSender sender1 = httpSenderProvider.createServerHttpSender();
        HttpSender sender2 = httpSenderProvider.createServerHttpSender();
        assertThat(sender1).isNotSameAs(sender2);
    }

}
