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

import ee.ria.xroad.common.conf.globalconf.GlobalConf;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.http.client.HttpClient;
import org.eclipse.jetty.server.Request;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class ServerProxyHandlerTest {

    @Test
    public void shouldExecuteClientProxyVersionCheck() throws Exception {
        final HttpServletRequest request = getMockedRequest();
        final Request baseRequest = mock(Request.class);
        ServerProxyHandler serverProxyHandler = new ServerProxyHandler(mock(HttpClient.class), mock(HttpClient.class));

        try (MockedStatic<GlobalConf> globalConfMock = mockStatic(GlobalConf.class);
                MockedStatic<ClientProxyVersionVerifier> checkMock = mockStatic(ClientProxyVersionVerifier.class)) {
            globalConfMock.when(GlobalConf::verifyValidity).then(invocationOnMock -> null);
            checkMock.when(() -> ClientProxyVersionVerifier.check(any()))
                    .thenAnswer(invocation -> null);

            serverProxyHandler.handle("target", baseRequest, request, getMockedResponse());

            checkMock.verify(() -> ClientProxyVersionVerifier.check(any()));
        }
    }

    private HttpServletRequest getMockedRequest() {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("remoteAddr");
        when(request.getMethod()).thenReturn("POST");
        return request;
    }

    private HttpServletResponse getMockedResponse() throws IOException {
        final HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getOutputStream()).thenReturn(mock(ServletOutputStream.class));
        return response;
    }
}
