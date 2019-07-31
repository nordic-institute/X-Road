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

import ee.ria.xroad.common.CodedExceptionWithHttpStatus;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.conf.serverconf.model.DescriptionType;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.util.MimeUtils;
import ee.ria.xroad.proxy.conf.KeyConf;
import ee.ria.xroad.proxy.testsuite.TestSuiteGlobalConf;
import ee.ria.xroad.proxy.testsuite.TestSuiteKeyConf;
import ee.ria.xroad.proxy.testsuite.TestSuiteServerConf;
import ee.ria.xroad.proxy.util.MetaserviceTestUtil;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.head;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_CLIENT_ID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link RestMetadataClientRequestProcessor}
 */
public class RestMetadataClientRequestProcessorTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private HttpServletRequest mockRequest;
    private HttpServletResponse mockResponse;
    private MetaserviceTestUtil.StubServletOutputStream mockServletOutputStream;

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8997);

    /**
     * Init data for tests
     */
    @Before
    public void init() throws IOException {

        GlobalConf.reload(new TestSuiteGlobalConf());
        KeyConf.reload(new TestSuiteKeyConf());
        ServerConf.reload(new TestSuiteServerConf() {
            @Override
            public DescriptionType getDescriptionType(ServiceId service) {
                return DescriptionType.REST;
            }
            @Override
            public String getServiceDescriptionURL(ServiceId service) {
                return "http://localhost:8997/foobar.json";
            }
        });

        mockRequest = mock(HttpServletRequest.class);
        mockResponse = mock(HttpServletResponse.class);
        mockServletOutputStream = new MetaserviceTestUtil.StubServletOutputStream();
        when(mockResponse.getOutputStream()).thenReturn(mockServletOutputStream);
    }

    @Test
    public void shouldBeAbleToProcessGetOpenAPI() throws Exception {
        List<String> keys = Arrays.asList("foo", "bar", "baz");
        when(mockRequest.getHeaderNames()).thenReturn(Collections.enumeration(keys));
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getRequestURI()).thenReturn("/r1/FI/COM/111/SERVICE/getOpenAPI");
        when(mockRequest.getQueryString()).thenReturn("serviceCode=foobar");
        RestMetadataClientRequestProcessor processor =
                new RestMetadataClientRequestProcessor(mockRequest, mockResponse);
        assertTrue(processor.canProcess());
    }

    @Test
    public void shouldNotBeAbleToProcessRandomRequest() throws Exception {
        List<String> keys = Arrays.asList("foo", "bar", "baz");
        when(mockRequest.getHeaderNames()).thenReturn(Collections.enumeration(keys));
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getRequestURI()).thenReturn("/r1/FI/COM/111/SERVICE/getRandom");
        when(mockRequest.getQueryString()).thenReturn("serviceCode=foobar");
        RestMetadataClientRequestProcessor processor =
                new RestMetadataClientRequestProcessor(mockRequest, mockResponse);
        assertFalse(processor.canProcess());
    }

    @Test
    public void shouldNotBeAbleToProcessPostRequest() throws Exception {
        List<String> keys = Arrays.asList("foo", "bar", "baz");
        when(mockRequest.getHeaderNames()).thenReturn(Collections.enumeration(keys));
        when(mockRequest.getMethod()).thenReturn("POST");
        when(mockRequest.getRequestURI()).thenReturn("/r1/FI/COM/111/SERVICE/getOpenAPI");
        when(mockRequest.getQueryString()).thenReturn("serviceCode=foobar");
        RestMetadataClientRequestProcessor processor =
                new RestMetadataClientRequestProcessor(mockRequest, mockResponse);
        assertFalse(processor.canProcess());
    }


    @Test
    public void shouldProcessGetOpenAPI() throws Exception {

        stubFor(head(urlPathMatching("/foobar.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")));

        stubFor(get(urlPathMatching("/foobar.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"height\": \"822\"}")));

        List<String> keys = Arrays.asList(HEADER_CLIENT_ID);
        when(mockRequest.getHeaderNames()).thenReturn(Collections.enumeration(keys));
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getRequestURI()).thenReturn("/r1/FI/COM/111/SERVICE/getOpenAPI");
        when(mockRequest.getQueryString()).thenReturn("name=john&serviceCode=foobar&weather=nice");
        when(mockRequest.getHeader(HEADER_CLIENT_ID)).thenReturn("FI/COM/111/CLIENT");
        when(mockRequest.getRequestURL())
                .thenReturn(new StringBuffer("http://ss/r1/FI/COM/111/SERVICE/getOpenAPI"));
        RestMetadataClientRequestProcessor processor =
                new RestMetadataClientRequestProcessor(mockRequest, mockResponse);
        assertTrue(processor.canProcess());
        processor.process();
        verify(mockResponse).setHeader(eq(MimeUtils.HEADER_QUERY_ID), anyString());
        verify(mockResponse).setHeader(eq(MimeUtils.HEADER_REQUEST_ID), anyString());
        verify(mockResponse).setHeader(eq(MimeUtils.HEADER_SERVICE_ID), anyString());
        verify(mockResponse).setHeader(eq(MimeUtils.HEADER_CLIENT_ID), anyString());
        verify(mockResponse).setHeader(eq(MimeUtils.HEADER_REQUEST_HASH), anyString());
        verify(mockResponse, never()).setHeader(eq(MimeUtils.HEADER_ERROR), anyString());
        verify(mockResponse).setContentType("application/json");
        verify(mockResponse).setStatus(200);
    }

    @Test
    public void shouldNotBeAbleToProcessGetOpenAPIWhenServerDoesNotReply() throws Exception {

        stubFor(head(urlPathMatching("/foobar.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")));

        stubFor(get(urlPathMatching("/foobar.json"))
                .willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));

        List<String> keys = Arrays.asList(HEADER_CLIENT_ID);
        when(mockRequest.getHeaderNames()).thenReturn(Collections.enumeration(keys));
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getRequestURI()).thenReturn("/r1/FI/COM/111/SERVICE/getOpenAPI");
        when(mockRequest.getQueryString()).thenReturn("serviceCode=foobar");
        when(mockRequest.getHeader(HEADER_CLIENT_ID)).thenReturn("FI/COM/111/CLIENT");
        when(mockRequest.getRequestURL())
                .thenReturn(new StringBuffer("http://ss/r1/FI/COM/111/SERVICE/getOpenAPI"));
        RestMetadataClientRequestProcessor processor =
                new RestMetadataClientRequestProcessor(mockRequest, mockResponse);
        assertTrue(processor.canProcess());
        try {
            processor.process();
        } catch (CodedExceptionWithHttpStatus ex) {
            verify(mockResponse).setContentType("application/json");
            verify(mockResponse, never()).setHeader(eq(MimeUtils.HEADER_QUERY_ID), anyString());
            verify(mockResponse, never()).setHeader(eq(MimeUtils.HEADER_REQUEST_ID), anyString());
            verify(mockResponse, never()).setHeader(eq(MimeUtils.HEADER_SERVICE_ID), anyString());
            verify(mockResponse, never()).setHeader(eq(MimeUtils.HEADER_CLIENT_ID), anyString());
            verify(mockResponse, never()).setHeader(eq(MimeUtils.HEADER_REQUEST_HASH), anyString());
            verify(mockResponse).setHeader(MimeUtils.HEADER_ERROR, "Server.ClientProxy.InternalError");
            assertEquals(500, ex.getStatus());
        }
    }

    @Test
    public void shouldNotBeAbleToProcessGetOpenAPIWhenQueryParamsInvalid() throws Exception {

        stubFor(head(urlPathMatching("/foobar.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")));

        stubFor(get(urlPathMatching("/foobar.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"height\": \"822\"}")));

        List<String> keys = Arrays.asList(HEADER_CLIENT_ID);
        when(mockRequest.getHeaderNames()).thenReturn(Collections.enumeration(keys));
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getRequestURI()).thenReturn("/r1/FI/COM/111/SERVICE/getOpenAPI");
        when(mockRequest.getQueryString()).thenReturn("foobar=baz");
        when(mockRequest.getHeader(HEADER_CLIENT_ID)).thenReturn("FI/COM/111/CLIENT");
        when(mockRequest.getRequestURL())
                .thenReturn(new StringBuffer("http://ss/r1/FI/COM/111/SERVICE/getOpenAPI"));
        RestMetadataClientRequestProcessor processor =
                new RestMetadataClientRequestProcessor(mockRequest, mockResponse);
        assertTrue(processor.canProcess());
        try {
            processor.process();
        } catch (CodedExceptionWithHttpStatus ex) {
            verify(mockResponse, never()).setHeader(eq(MimeUtils.HEADER_QUERY_ID), anyString());
            verify(mockResponse, never()).setHeader(eq(MimeUtils.HEADER_REQUEST_ID), anyString());
            verify(mockResponse, never()).setHeader(eq(MimeUtils.HEADER_SERVICE_ID), anyString());
            verify(mockResponse, never()).setHeader(eq(MimeUtils.HEADER_CLIENT_ID), anyString());
            verify(mockResponse, never()).setHeader(eq(MimeUtils.HEADER_REQUEST_HASH), anyString());
            verify(mockResponse).setHeader(MimeUtils.HEADER_ERROR, "Server.ClientProxy.BadRequest");
            assertEquals(400, ex.getStatus());
        }
    }

    @Test
    public void shouldNotBeAbleToProcessGetOpenAPIWhenDescriptionTypeNotRest() throws Exception {

        ServerConf.reload(new TestSuiteServerConf() {
            @Override
            public DescriptionType getDescriptionType(ServiceId service) {
                return DescriptionType.OPENAPI3;
            }
            @Override
            public String getServiceDescriptionURL(ServiceId service) {
                return "http://localhost:8997/foobar.json";
            }
        });

        stubFor(head(urlPathMatching("/foobar.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")));

        stubFor(get(urlPathMatching("/foobar.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"height\": \"822\"}")));

        List<String> keys = Arrays.asList(HEADER_CLIENT_ID);
        when(mockRequest.getHeaderNames()).thenReturn(Collections.enumeration(keys));
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getRequestURI()).thenReturn("/r1/FI/COM/111/SERVICE/getOpenAPI");
        when(mockRequest.getQueryString()).thenReturn("name=john&serviceCode=foobar&weather=nice");
        when(mockRequest.getHeader(HEADER_CLIENT_ID)).thenReturn("FI/COM/111/CLIENT");
        when(mockRequest.getRequestURL())
                .thenReturn(new StringBuffer("http://ss/r1/FI/COM/111/SERVICE/getOpenAPI"));
        RestMetadataClientRequestProcessor processor =
                new RestMetadataClientRequestProcessor(mockRequest, mockResponse);
        assertTrue(processor.canProcess());
        try {
            processor.process();
        } catch (CodedExceptionWithHttpStatus ex) {
            verify(mockResponse, never()).setHeader(eq(MimeUtils.HEADER_QUERY_ID), anyString());
            verify(mockResponse, never()).setHeader(eq(MimeUtils.HEADER_REQUEST_ID), anyString());
            verify(mockResponse, never()).setHeader(eq(MimeUtils.HEADER_SERVICE_ID), anyString());
            verify(mockResponse, never()).setHeader(eq(MimeUtils.HEADER_CLIENT_ID), anyString());
            verify(mockResponse, never()).setHeader(eq(MimeUtils.HEADER_REQUEST_HASH), anyString());
            verify(mockResponse).setHeader(MimeUtils.HEADER_ERROR, "Server.ClientProxy.InternalError");
            assertEquals(500, ex.getStatus());
        }
    }

    @Test
    public void shouldNotBeAbleToProcessGetOpenAPIWhenServiceNotFound() throws Exception {

        stubFor(head(urlPathMatching("/foobar.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")));

        stubFor(get(urlPathMatching("/foobar.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"height\": \"822\"}")));

        List<String> keys = Arrays.asList(HEADER_CLIENT_ID);
        when(mockRequest.getHeaderNames()).thenReturn(Collections.enumeration(keys));
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getRequestURI()).thenReturn("/r1/FI/COM/111/SERVICE/getOpenAPI");
        when(mockRequest.getQueryString()).thenReturn("serviceCode=qwerty");
        when(mockRequest.getHeader(HEADER_CLIENT_ID)).thenReturn("FI/COM/111/CLIENT");
        when(mockRequest.getRequestURL())
                .thenReturn(new StringBuffer("http://ss/r1/FI/COM/111/SERVICE/getOpenAPI"));
        RestMetadataClientRequestProcessor processor =
                new RestMetadataClientRequestProcessor(mockRequest, mockResponse);
        assertTrue(processor.canProcess());
        try {
            processor.process();
        } catch (CodedExceptionWithHttpStatus ex) {
            verify(mockResponse, never()).setHeader(eq(MimeUtils.HEADER_QUERY_ID), anyString());
            verify(mockResponse, never()).setHeader(eq(MimeUtils.HEADER_REQUEST_ID), anyString());
            verify(mockResponse, never()).setHeader(eq(MimeUtils.HEADER_SERVICE_ID), anyString());
            verify(mockResponse, never()).setHeader(eq(MimeUtils.HEADER_CLIENT_ID), anyString());
            verify(mockResponse, never()).setHeader(eq(MimeUtils.HEADER_REQUEST_HASH), anyString());
            verify(mockResponse).setHeader(MimeUtils.HEADER_ERROR, "Server.ClientProxy.InternalError");
            assertEquals(500, ex.getStatus());
        }
    }
}
