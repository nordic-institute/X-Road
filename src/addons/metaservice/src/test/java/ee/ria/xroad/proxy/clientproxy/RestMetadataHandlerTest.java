/**
 * The MIT License
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
package ee.ria.xroad.proxy.clientproxy;

import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.message.RestMessage;
import ee.ria.xroad.proxy.conf.KeyConf;
import ee.ria.xroad.proxy.testsuite.TestSuiteGlobalConf;
import ee.ria.xroad.proxy.testsuite.TestSuiteKeyConf;
import ee.ria.xroad.proxy.util.MessageProcessorBase;

import org.apache.http.client.HttpClient;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static ee.ria.xroad.common.util.MimeUtils.HEADER_CLIENT_ID;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link ee.ria.xroad.proxy.clientproxy.MetadataHandler}
 */
public class RestMetadataHandlerTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private HttpClient httpClientMock;
    private HttpServletRequest mockRequest;
    private HttpServletResponse mockResponse;

    /**
     * Init common data for tests
     */
    @Before
    public void init() {
        GlobalConf.reload(new TestSuiteGlobalConf());
        KeyConf.reload(new TestSuiteKeyConf());

        httpClientMock = mock(HttpClient.class);
        mockRequest = mock(HttpServletRequest.class);
        mockResponse = mock(HttpServletResponse.class);

    }

    @Test
    public void shouldReturnProcessorWhenAbleToProcess() throws Exception {

        List<String> keys = Arrays.asList(HEADER_CLIENT_ID);
        when(mockRequest.getHeaderNames()).thenReturn(Collections.enumeration(keys));

        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getHeader(HEADER_CLIENT_ID)).thenReturn("FI/COM/111/CLIENT");

        when(mockRequest.getRequestURI()).thenReturn("/r1/FI/COM/111/SERVICE/getOpenAPI");
        when(mockRequest.getQueryString()).thenReturn("serviceCode=foobar");

        RestMetadataHandler handlerToTest = new RestMetadataHandler(httpClientMock);

        String target = "/r" + RestMessage.PROTOCOL_VERSION + "/foobar";

        MessageProcessorBase returnValue = handlerToTest.createRequestProcessor(
                target, mockRequest, mockResponse, null);

        assertNotNull("Was expecting a non-null return value", returnValue);
    }

    @Test
    public void shouldNotReturnProcessorWhenRequestNotRest() throws Exception {

        List<String> keys = Arrays.asList(HEADER_CLIENT_ID);
        when(mockRequest.getHeaderNames()).thenReturn(Collections.enumeration(keys));

        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getHeader(HEADER_CLIENT_ID)).thenReturn("FI/COM/111/CLIENT");

        when(mockRequest.getRequestURI()).thenReturn("/getWsdl");
        when(mockRequest.getQueryString()).thenReturn("serviceCode=foobar");

        RestMetadataHandler handlerToTest = new RestMetadataHandler(httpClientMock);

        String target = "/getWsdl";

        MessageProcessorBase returnValue = handlerToTest.createRequestProcessor(
                target, mockRequest, mockResponse, null);

        assertNull("Was expecting a null return value", returnValue);
    }

    @Test
    public void shouldNotCreateProcessorWhenMethodUnsupported() throws Exception {

        List<String> keys = Arrays.asList(HEADER_CLIENT_ID);
        when(mockRequest.getHeaderNames()).thenReturn(Collections.enumeration(keys));

        when(mockRequest.getMethod()).thenReturn("POST");
        when(mockRequest.getHeader(HEADER_CLIENT_ID)).thenReturn("FI/COM/111/CLIENT");

        when(mockRequest.getRequestURI()).thenReturn("/r1/FI/COM/111/SERVICE/getOpenAPI");
        when(mockRequest.getQueryString()).thenReturn("serviceCode=foobar");

        RestMetadataHandler handlerToTest = new RestMetadataHandler(httpClientMock);

        String target = "/r" + RestMessage.PROTOCOL_VERSION + "/foobar";

        MessageProcessorBase returnValue = handlerToTest.createRequestProcessor(
                target, mockRequest, mockResponse, null);

        assertNull("Was expecting a null return value", returnValue);
    }

    @Test
    public void shouldNotCreateProcessorWhenClientHeaderMissing() throws Exception {

        List<String> keys = Arrays.asList(HEADER_CLIENT_ID);
        when(mockRequest.getHeaderNames()).thenReturn(Collections.enumeration(keys));

        when(mockRequest.getMethod()).thenReturn("POST");
        when(mockRequest.getHeader(HEADER_CLIENT_ID)).thenReturn(null);

        when(mockRequest.getRequestURI()).thenReturn("/r1/FI/COM/111/SERVICE/getOpenAPI");
        when(mockRequest.getQueryString()).thenReturn("serviceCode=foobar");

        RestMetadataHandler handlerToTest = new RestMetadataHandler(httpClientMock);

        String target = "/r" + RestMessage.PROTOCOL_VERSION + "/foobar";

        MessageProcessorBase returnValue = handlerToTest.createRequestProcessor(
                target, mockRequest, mockResponse, null);

        assertNull("Was expecting a null return value", returnValue);
    }
}
