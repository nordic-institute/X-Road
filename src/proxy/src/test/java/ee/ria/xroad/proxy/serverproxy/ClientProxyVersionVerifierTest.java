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
import ee.ria.xroad.common.util.MimeUtils;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.server.ConnectionMetaData;
import org.eclipse.jetty.server.Request;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ClientProxyVersionVerifierTest {
    private static final String CLIENT_VERSION_6_26_3 = "6.26.3";
    private static final String CLIENT_VERSION_7_1_3 = "7.1.3";
    private static final String VERSION_7_1_3 = "7.1.3";
    private static final String MIN_SUPPORTED_CLIENT_VERSION = "xroad.proxy.server-min-supported-client-version";

    private Request request;
    private HttpFields headers;

    @Before
    public void initTest() {
        request = mock(Request.class);
        headers = mock(HttpFields.class);
        var connectionMetaData = mock(ConnectionMetaData.class);
        when(request.getHeaders()).thenReturn(headers);
        when(request.getConnectionMetaData()).thenReturn(connectionMetaData);
    }

    @Test
    public void whenMinSupportedClientVersionPropertyIsEmptyThenShouldPassClientProxyVersionCheck() {
        when(headers.get(MimeUtils.HEADER_PROXY_VERSION)).thenReturn(CLIENT_VERSION_7_1_3);

        ClientProxyVersionVerifier.check(request);
    }

    @Test
    public void shouldPassClientProxyVersionCheck() {
        System.setProperty(MIN_SUPPORTED_CLIENT_VERSION, VERSION_7_1_3);
        when(headers.get(MimeUtils.HEADER_PROXY_VERSION)).thenReturn(CLIENT_VERSION_7_1_3);

        ClientProxyVersionVerifier.check(request);
    }

    @Test
    public void shouldRaiseError() {
        System.setProperty(MIN_SUPPORTED_CLIENT_VERSION, VERSION_7_1_3);
        when(headers.get(MimeUtils.HEADER_PROXY_VERSION)).thenReturn(CLIENT_VERSION_6_26_3);

        CodedException exception = assertThrows(CodedException.class, () -> ClientProxyVersionVerifier.check(request));
        assertEquals("ClientProxyVersionNotSupported: The minimum supported version for client security server is: 7.1.3 ",
                exception.getMessage());
    }
}
