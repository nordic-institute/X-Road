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

import org.apache.http.client.HttpClient;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.server.ConnectionMetaData;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.junit.Test;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.impl.cert.CertChainFactory;
import org.niis.xroad.keyconf.KeyConfProvider;
import org.niis.xroad.proxy.core.util.CommonBeanProxy;
import org.niis.xroad.serverconf.ServerConfProvider;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ServerProxyHandlerTest {

    @Test
    public void shouldExecuteClientProxyVersionCheck() throws Exception {
        final var request = getMockedRequest();
        final var callback = mock(Callback.class);
        var globalConfProvider = mock(GlobalConfProvider.class);
        var keyConfProvider = mock(KeyConfProvider.class);
        var serverConfProvider = mock(ServerConfProvider.class);
        var certChainFactory = mock(CertChainFactory.class);
        var checkMock = mock(ClientProxyVersionVerifier.class);
        var commonBeanProxy = new CommonBeanProxy(globalConfProvider, serverConfProvider, keyConfProvider, null,
                certChainFactory, null);

        ServerProxyHandler serverProxyHandler = new ServerProxyHandler(commonBeanProxy, mock(HttpClient.class), mock(HttpClient.class),
                checkMock);

        serverProxyHandler.handle(request, getMockedResponse(), callback);

        verify(checkMock).check(any());
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
