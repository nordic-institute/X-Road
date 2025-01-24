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
package ee.ria.xroad.proxy.clientproxy;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.cert.CertChainFactory;
import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;
import ee.ria.xroad.common.conf.serverconf.ServerConfProvider;
import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.ResponseWrapper;
import ee.ria.xroad.proxy.conf.KeyConfProvider;
import ee.ria.xroad.proxy.testsuite.TestSuiteGlobalConf;
import ee.ria.xroad.proxy.testsuite.TestSuiteKeyConf;
import ee.ria.xroad.proxy.util.MessageProcessorBase;

import org.apache.http.client.HttpClient;
import org.eclipse.jetty.http.HttpURI;
import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Optional;

import static ee.ria.xroad.common.ErrorCodes.X_INVALID_REQUEST;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasProperty;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link ee.ria.xroad.proxy.clientproxy.MetadataHandler}
 */
public class MetadataHandlerTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private HttpClient httpClientMock;
    private RequestWrapper mockRequest;
    private HttpURI mockHttpUri;
    private ResponseWrapper mockResponse;


    private GlobalConfProvider globalConfProvider;
    private KeyConfProvider keyConfProvider;
    private ServerConfProvider serverConfProvider;
    private CertChainFactory certChainFactory;

    /**
     * Init common data for tests
     */
    @Before
    public void init() {
        globalConfProvider = new TestSuiteGlobalConf();
        keyConfProvider = new TestSuiteKeyConf(globalConfProvider);
        serverConfProvider = mock(ServerConfProvider.class);
        certChainFactory = mock(CertChainFactory.class);

        httpClientMock = mock(HttpClient.class);
        mockRequest = mock(RequestWrapper.class);
        mockResponse = mock(ResponseWrapper.class);
        mockHttpUri = mock(HttpURI.class);

        when(mockRequest.getHttpURI()).thenReturn(mockHttpUri);
        when(mockHttpUri.getPath()).thenReturn("/target");

    }

    @Test
    public void shouldNotCreateProcessorForPostRequest() {

        when(mockRequest.getMethod()).thenReturn("POST");

        MetadataHandler handlerToTest = new MetadataHandler(globalConfProvider, keyConfProvider, serverConfProvider, certChainFactory,
                httpClientMock);


        Optional<MessageProcessorBase> returnValue =
                handlerToTest.createRequestProcessor(mockRequest, mockResponse, null);

        assertTrue("Was expecting a null return value", returnValue.isEmpty());
    }

    @Test
    public void shouldNotCreateProcessorForUnprocessableRequest() {

        when(mockRequest.getMethod()).thenReturn("GET");

        MetadataHandler handlerToTest = new MetadataHandler(globalConfProvider, keyConfProvider, serverConfProvider, certChainFactory,
                httpClientMock);


        Optional<MessageProcessorBase> returnValue =
                handlerToTest.createRequestProcessor(mockRequest, mockResponse, null);

        assertTrue("Was expecting a null return value", returnValue.isEmpty());
    }

    @Test
    public void shouldThrowWhenTargetNull() {

        MetadataHandler handlerToTest = new MetadataHandler(globalConfProvider, keyConfProvider, serverConfProvider, certChainFactory,
                httpClientMock);
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockHttpUri.getPath()).thenReturn(null);

        thrown.expect(CodedException.class);
        thrown.expect(hasProperty("faultCode", is(X_INVALID_REQUEST)));
        thrown.expectMessage(CoreMatchers.containsString("Target must not be null"));


        handlerToTest.createRequestProcessor(mockRequest, mockResponse, null);
    }


    @Test
    public void shouldReturnProcessorWhenAbleToProcess() {

        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockHttpUri.getPath()).thenReturn("/listClients");

        MetadataHandler handlerToTest = new MetadataHandler(globalConfProvider, keyConfProvider, serverConfProvider, certChainFactory,
                httpClientMock);

        Optional<MessageProcessorBase> result = handlerToTest.createRequestProcessor(mockRequest, mockResponse, null);

        assertTrue("Was expecting actual message processor", result.isPresent());

        assertThat("Message processor is of wrong type", result.get(),
                instanceOf(MetadataClientRequestProcessor.class));
    }

}
