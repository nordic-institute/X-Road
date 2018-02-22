/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.identifier.CentralServiceId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.util.MimeUtils;
import ee.ria.xroad.proxy.conf.KeyConf;
import ee.ria.xroad.proxy.testsuite.TestGlobalConf;
import ee.ria.xroad.proxy.testsuite.TestKeyConf;
import ee.ria.xroad.proxy.testsuite.TestServerConf;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.apache.http.HttpHeaders;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ProvideSystemProperty;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.xml.sax.InputSource;

import javax.net.ServerSocketFactory;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.wsdl.Definition;
import javax.wsdl.factory.WSDLFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_REQUEST;
import static ee.ria.xroad.common.util.MimeTypes.MULTIPART_RELATED;
import static ee.ria.xroad.common.util.MimeTypes.TEXT_XML;
import static ee.ria.xroad.common.util.MimeTypes.TEXT_XML_UTF8;
import static ee.ria.xroad.proxy.util.MetaserviceTestUtil.CodedExceptionMatcher.faultCodeEquals;
import static ee.ria.xroad.proxy.util.MetaserviceTestUtil.StubServletOutputStream;
import static ee.ria.xroad.proxy.util.MetaserviceTestUtil.parseOperationNamesFromWSDLDefinition;
import static java.util.Collections.singletonList;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.isIn;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link MetadataClientRequestProcessor}
 */
public class WsdlRequestProcessorTest {

    private static final String EXPECTED_XR_INSTANCE = "EE";


    private static final int WSDL_SERVER_PORT;
    static {
        try (ServerSocket s = ServerSocketFactory.getDefault().createServerSocket(0)) {
            s.setReuseAddress(true);
            WSDL_SERVER_PORT = s.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to select port");
        }
    }

    private static final String EXPECTED_WSDL_QUERY_PATH = "/";


    private WireMockServer mockServer;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Rule // by default, the request processor contacts a client proxy
    public final ProvideSystemProperty targetServerProperty
            = new ProvideSystemProperty(SystemProperties.PROXY_CLIENT_HTTP_PORT, Integer.toString(WSDL_SERVER_PORT));

    @Rule
    public final ProvideSystemProperty keepAlive = new ProvideSystemProperty("http.keepAlive", "false");

    private HttpServletRequest mockRequest;
    private HttpServletResponse mockResponse;
    private StubServletOutputStream mockServletOutputStream;

    /**
     * Init data for tests
     */
    @Before
    public void init() throws IOException {

        GlobalConf.reload(new TestGlobalConf());
        KeyConf.reload(new TestKeyConf());

        mockRequest = mock(HttpServletRequest.class);
        mockResponse = mock(HttpServletResponse.class);
        mockServletOutputStream = new StubServletOutputStream();
        when(mockResponse.getOutputStream()).thenReturn(mockServletOutputStream);
        this.mockServer = new WireMockServer(options().port(WSDL_SERVER_PORT));
    }

    @After
    public void tearDown() {
     this.mockServer.stop();
    }


    @Test
    public void shouldCreateConnection() throws Exception {

        // setup
        SoapMessageImpl mockSoapMessage = mock(SoapMessageImpl.class);

        mockServer.stubFor(WireMock.any(urlPathEqualTo(EXPECTED_WSDL_QUERY_PATH))
                .willReturn(aResponse()));
        mockServer.start();


        final String expectedMessage = "expectedMessage135122";
        when(mockSoapMessage.getBytes()).thenReturn(expectedMessage.getBytes());


        WsdlRequestProcessor processorToTest = new WsdlRequestProcessor(mockRequest, mockResponse);

        // execution

        processorToTest.createConnection(mockSoapMessage);

        // verification

        mockServer.verify(postRequestedFor(urlEqualTo(EXPECTED_WSDL_QUERY_PATH))
                .withHeader(HttpHeaders.CONTENT_TYPE, equalTo(TEXT_XML_UTF8))
                .withRequestBody(equalTo(expectedMessage)));
    }


    @Test
    public void shouldThrowIfProxyResponseNotOk() throws Exception {

        // setup
        SoapMessageImpl mockSoapMessage = mock(SoapMessageImpl.class);

        final String expectedErrorMessage = "Some error happened!";
        final int expectedErrorCode = SC_INTERNAL_SERVER_ERROR;

        mockServer.stubFor(WireMock.any(urlPathEqualTo(EXPECTED_WSDL_QUERY_PATH))
                .willReturn(WireMock.aResponse().withStatus(expectedErrorCode)
                        .withStatusMessage(expectedErrorMessage)));
        mockServer.start();

        when(mockSoapMessage.getBytes()).thenReturn("justsomething".getBytes());


        WsdlRequestProcessor processorToTest = new WsdlRequestProcessor(mockRequest, mockResponse);

        thrown.expectMessage(is("Received HTTP error: " + expectedErrorCode + " - " + expectedErrorMessage));

        // execution

        processorToTest.createConnection(mockSoapMessage);

        // expecting an exception..
    }

    @Test
    public void shouldGetServiceId() throws Exception {

        // setup

        final ServiceId expectedServiceId = ServiceId.create(EXPECTED_XR_INSTANCE, "someMember",
                "serviceCode3322", "subsystem3",
                "serviceCode3", "version1.1");

        when(mockRequest.getParameter(eq(WsdlRequestProcessor.PARAM_INSTANCE_IDENTIFIER)))
                .thenReturn(expectedServiceId.getXRoadInstance());

        when(mockRequest.getParameter(eq(WsdlRequestProcessor.PARAM_MEMBER_CLASS)))
                .thenReturn(expectedServiceId.getMemberClass());

        when(mockRequest.getParameter(eq(WsdlRequestProcessor.PARAM_MEMBER_CODE)))
                .thenReturn(expectedServiceId.getMemberCode());

        when(mockRequest.getParameter(eq(WsdlRequestProcessor.PARAM_SERVICE_CODE)))
                .thenReturn(expectedServiceId.getServiceCode());

        when(mockRequest.getParameter(eq(WsdlRequestProcessor.PARAM_SUBSYSTEM_CODE)))
                .thenReturn(expectedServiceId.getSubsystemCode());

        when(mockRequest.getParameter(eq(WsdlRequestProcessor.PARAM_VERSION)))
                .thenReturn(expectedServiceId.getServiceVersion());

        WsdlRequestProcessor processorToTest = new WsdlRequestProcessor(mockRequest, mockResponse);

        // execution

        ServiceId serviceId = processorToTest.getServiceId();

        // verification

        assertThat("Service id does not match", serviceId, is(expectedServiceId));
    }

    @Test
    public void shouldGetCentralServiceIdWhenNoMemberClassOrCode() throws Exception {

        // setup

        final CentralServiceId expectedCentralServiceId =
                CentralServiceId.create(EXPECTED_XR_INSTANCE, "serviceCode3322");

        when(mockRequest.getParameter(eq(WsdlRequestProcessor.PARAM_INSTANCE_IDENTIFIER)))
                .thenReturn(expectedCentralServiceId.getXRoadInstance());

             when(mockRequest.getParameter(eq(WsdlRequestProcessor.PARAM_SERVICE_CODE)))
                .thenReturn(expectedCentralServiceId.getServiceCode());

        WsdlRequestProcessor processorToTest = new WsdlRequestProcessor(mockRequest, mockResponse);

        // execution

        ServiceId serviceId = processorToTest.getServiceId();

        // verification

        assertThat("Service id does not match", serviceId, is(expectedCentralServiceId));
    }

    @Test
    public void shouldNotGetServiceIdWhenMissingXroadInstance() throws Exception {

        // setup

        when(mockRequest.getParameter(eq(WsdlRequestProcessor.PARAM_INSTANCE_IDENTIFIER)))
                .thenReturn("");

        WsdlRequestProcessor processorToTest = new WsdlRequestProcessor(mockRequest, mockResponse);

        thrown.expect(CodedException.class);
        thrown.expect(faultCodeEquals(X_INVALID_REQUEST));
        thrown.expectMessage(containsString("Must specify instance identifier"));

        // execution

        processorToTest.getServiceId();

        // expecting an exception..

    }


    @Test
    public void shouldNotGetServiceIdWhenMissingServiceCode() throws Exception {

        // setup
        when(mockRequest.getParameter(eq(WsdlRequestProcessor.PARAM_INSTANCE_IDENTIFIER)))
                .thenReturn(EXPECTED_XR_INSTANCE);

        when(mockRequest.getParameter(eq(WsdlRequestProcessor.PARAM_SERVICE_CODE)))
                .thenReturn("  ");

        WsdlRequestProcessor processorToTest = new WsdlRequestProcessor(mockRequest, mockResponse);

        thrown.expect(CodedException.class);
        thrown.expect(faultCodeEquals(X_INVALID_REQUEST));
        thrown.expectMessage(containsString("Must specify service code"));

        // execution

        processorToTest.getServiceId();

        // expecting an exception..
    }

    @Test
    public void shouldNotGetServiceIdWhenMissingMemberCode() throws Exception {

        // setup
        when(mockRequest.getParameter(eq(WsdlRequestProcessor.PARAM_INSTANCE_IDENTIFIER)))
                .thenReturn(EXPECTED_XR_INSTANCE);

        when(mockRequest.getParameter(eq(WsdlRequestProcessor.PARAM_MEMBER_CLASS)))
                .thenReturn("government");

        when(mockRequest.getParameter(eq(WsdlRequestProcessor.PARAM_SERVICE_CODE)))
                .thenReturn("someServiceCode");

        WsdlRequestProcessor processorToTest = new WsdlRequestProcessor(mockRequest, mockResponse);

        thrown.expect(CodedException.class);
        thrown.expect(faultCodeEquals(X_INVALID_REQUEST));
        thrown.expectMessage(containsString("InvalidRequest: 'memberCode' must not be blank"));

        // execution

        processorToTest.getServiceId();

        // expecting an exception..
    }

    @Test
    public void shouldGetWsdl() throws Exception {

        // setup

        mockServer.stubFor(WireMock.any(urlPathEqualTo(EXPECTED_WSDL_QUERY_PATH))
                .willReturn(aResponse()
                        //file under resources/__files
                        .withBodyFile("wsdl_response.xml")
                        // this needs to match the SOAP message boundary in the file
                        .withHeader(HttpHeaders.CONTENT_TYPE, MULTIPART_RELATED
                                + "; type=\"text/xml\"; charset=UTF-8; boundary=xroadZTLLyIMMYnAYliBumWCqHJYAhutxNf")));
        mockServer.start();

        final SecurityServerId providedIdentifier = SecurityServerId.create(EXPECTED_XR_INSTANCE,
                "memberClassGov", "memberCode11", "serverCode_");

        ServerConf.reload(new TestServerConf() {
            @Override
            public SecurityServerId getIdentifier() {
                return providedIdentifier;
            }
        });

        final ServiceId expectedServiceId = ServiceId.create(EXPECTED_XR_INSTANCE, "someMember",
                "serviceCode3322", "subsystem3",
                "serviceCode3", "version1.1");

        when(mockRequest.getParameter(eq(WsdlRequestProcessor.PARAM_INSTANCE_IDENTIFIER)))
                .thenReturn(expectedServiceId.getXRoadInstance());

        when(mockRequest.getParameter(eq(WsdlRequestProcessor.PARAM_MEMBER_CLASS)))
                .thenReturn(expectedServiceId.getMemberClass());

        when(mockRequest.getParameter(eq(WsdlRequestProcessor.PARAM_MEMBER_CODE)))
                .thenReturn(expectedServiceId.getMemberCode());

        when(mockRequest.getParameter(eq(WsdlRequestProcessor.PARAM_SERVICE_CODE)))
                .thenReturn(expectedServiceId.getServiceCode());

        when(mockRequest.getParameter(eq(WsdlRequestProcessor.PARAM_SUBSYSTEM_CODE)))
                .thenReturn(expectedServiceId.getSubsystemCode());

        when(mockRequest.getParameter(eq(WsdlRequestProcessor.PARAM_VERSION)))
                .thenReturn(expectedServiceId.getServiceVersion());

        final List<String> expectedWSDLServiceNames =
                Arrays.asList("getRandom", "helloService");

        WsdlRequestProcessor processorToTest = new WsdlRequestProcessor(mockRequest, mockResponse);

        // execution

        processorToTest.process();

        // verification

        assertContentTypeIsIn(singletonList(TEXT_XML));

        Definition definition = WSDLFactory.newInstance().newWSDLReader()
                .readWSDL(null, new InputSource(mockServletOutputStream.getAsInputStream()));

        List<String> operationNames = parseOperationNamesFromWSDLDefinition(definition);

        assertThat("Expected to find certain operations",
                operationNames,
                containsInAnyOrder(expectedWSDLServiceNames.toArray()));

    }

    @Test
    public void shouldThrowWhenReceivingSoapFault() throws Exception {

        // setup

        final String expectedMessage = "That was an invalid request, buddy!";
        final String expectedErrorCode = X_INVALID_REQUEST;

        final CodedException generatedEx = new CodedException(expectedErrorCode, expectedMessage);

        mockServer.stubFor(WireMock.any(urlPathEqualTo(EXPECTED_WSDL_QUERY_PATH))
                .willReturn(aResponse()
                        .withBody(SoapFault.createFaultXml(generatedEx).getBytes(MimeUtils.UTF8))
                        .withHeader(HttpHeaders.CONTENT_TYPE, TEXT_XML_UTF8)));
        mockServer.start();

        final SecurityServerId providedIdentifier = SecurityServerId.create(EXPECTED_XR_INSTANCE,
                "memberClassGov", "memberCode11", "serverCode_");

        ServerConf.reload(new TestServerConf() {
            @Override
            public SecurityServerId getIdentifier() {
                return providedIdentifier;
            }
        });

        final CentralServiceId expectedCentralServiceId =
                CentralServiceId.create(EXPECTED_XR_INSTANCE, "serviceCode3322");

        when(mockRequest.getParameter(eq(WsdlRequestProcessor.PARAM_INSTANCE_IDENTIFIER)))
                .thenReturn(expectedCentralServiceId.getXRoadInstance());

        when(mockRequest.getParameter(eq(WsdlRequestProcessor.PARAM_SERVICE_CODE)))
                .thenReturn(expectedCentralServiceId.getServiceCode());

        WsdlRequestProcessor processorToTest = new WsdlRequestProcessor(mockRequest, mockResponse);

        thrown.expect(CodedException.class);
        thrown.expect(faultCodeEquals(expectedErrorCode));
        thrown.expectMessage(allOf(containsString(expectedErrorCode), containsString(expectedMessage)));


        // execution

        processorToTest.process();

        // expecting an exception..
    }

    private void assertContentTypeIsIn(List<String> allowedContentTypes) {
        ArgumentCaptor<String> contentTypeCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockResponse).setContentType(contentTypeCaptor.capture());
        assertThat("Wrong content type", contentTypeCaptor.getValue(), isIn(allowedContentTypes));
    }

}
