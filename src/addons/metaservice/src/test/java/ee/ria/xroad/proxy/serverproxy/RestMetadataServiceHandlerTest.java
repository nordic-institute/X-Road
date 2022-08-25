/**
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
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.conf.serverconf.model.DescriptionType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.RestRequest;
import ee.ria.xroad.common.message.RestResponse;
import ee.ria.xroad.common.metadata.RestServiceDetailsListType;
import ee.ria.xroad.common.opmonitoring.OpMonitoringData;
import ee.ria.xroad.common.util.CachingStream;
import ee.ria.xroad.proxy.conf.KeyConf;
import ee.ria.xroad.proxy.protocol.ProxyMessage;
import ee.ria.xroad.proxy.protocol.ProxyMessageDecoder;
import ee.ria.xroad.proxy.protocol.ProxyMessageEncoder;
import ee.ria.xroad.proxy.testsuite.TestSuiteGlobalConf;
import ee.ria.xroad.proxy.testsuite.TestSuiteKeyConf;
import ee.ria.xroad.proxy.testsuite.TestSuiteServerConf;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static ee.ria.xroad.common.metadata.MetadataRequests.ALLOWED_METHODS;
import static ee.ria.xroad.common.metadata.MetadataRequests.GET_OPENAPI;
import static ee.ria.xroad.common.metadata.MetadataRequests.LIST_METHODS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link RestMetadataServiceHandlerImpl}
 */
@Slf4j
public class RestMetadataServiceHandlerTest {


    private static final String EXPECTED_XR_INSTANCE = "EE";
    private static final String SUBSYSTEM_FOR_YAML_FILE = "YAMLSUBSYSTEM";
    private static final String SUBSYSTEM_FOR_JSON_FILE = "JSONSUBSYSTEM";
    private static final String SUBSYSTEM_FOR_UNSUPPORTED_YAML_FILE = "UNSUPPORTEDYAMLFILE";
    private static final ClientId.Conf DEFAULT_CLIENT = ClientId.Conf.create(EXPECTED_XR_INSTANCE, "GOV",
            "1234TEST_CLIENT", SUBSYSTEM_FOR_YAML_FILE);
    private static final ClientId.Conf SECONDARY_CLIENT = ClientId.Conf.create(EXPECTED_XR_INSTANCE, "GOV",
            "1234TEST_CLIENT", SUBSYSTEM_FOR_JSON_FILE);
    private static final ClientId.Conf CLIENT_WITH_UNSUPPORTED_OPENAPI = ClientId.Conf.create(EXPECTED_XR_INSTANCE, "GOV",
            "1234TEST_CLIENT", SUBSYSTEM_FOR_UNSUPPORTED_YAML_FILE);
    private static final byte[] REQUEST_HASH = "foobar1234".getBytes();
    private static final int MOCK_SERVER_PORT = 9858;

    static final ObjectMapper MAPPER;

    static {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
        MAPPER = mapper;
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private HttpClient httpClientMock;
    private HttpServletRequest mockRequest;
    private HttpServletResponse mockResponse;
    private ProxyMessage mockProxyMessage;
    private WireMockServer mockServer;


    /**
     * Init class-wide test instances
     */
    @BeforeClass
    public static void initCommon() {
    }

    /**
     * Init data for tests
     */
    @Before
    public void init() {

        GlobalConf.reload(new TestSuiteGlobalConf());
        KeyConf.reload(new TestSuiteKeyConf());
        ServerConf.reload(new TestSuiteServerConf() {
            @Override
            public DescriptionType getDescriptionType(ServiceId service) {
                return DescriptionType.OPENAPI3;
            }
            @Override
            public String getServiceDescriptionURL(ServiceId service) {
                if (SUBSYSTEM_FOR_JSON_FILE.equals(service.getSubsystemCode())) {
                    return "http://localhost:9858/petstore.json";
                } else if (SUBSYSTEM_FOR_UNSUPPORTED_YAML_FILE.equals(service.getSubsystemCode())) {
                    return "http://localhost:9858/openapi_incompatible_version.yaml";
                } else {
                    return "http://localhost:9858/petstore.yaml";
                }
            }
        });

        httpClientMock = mock(HttpClient.class);
        mockRequest = mock(HttpServletRequest.class);
        mockResponse = mock(HttpServletResponse.class);
        mockProxyMessage = mock(ProxyMessage.class);

        mockServer = new WireMockServer(options().port(MOCK_SERVER_PORT));

        mockServer.stubFor(WireMock.any(urlPathEqualTo("/petstore.json"))
                .willReturn(aResponse().withBodyFile("petstore.json")));

        mockServer.stubFor(WireMock.any(urlPathEqualTo("/petstore.yaml"))
                .willReturn(aResponse().withBodyFile("petstore.yaml")));

        mockServer.start();
    }

    @After
    public void tearDown() {
        mockServer.stop();
    }

    @Test
    public void shouldBeAbleToHandleListMethods() {
        RestMetadataServiceHandlerImpl handlerToTest = new RestMetadataServiceHandlerImpl();
        ServiceId.Conf serviceId = ServiceId.Conf.create(DEFAULT_CLIENT, LIST_METHODS);
        RestRequest mockRestRequest = mock(RestRequest.class);
        when(mockRestRequest.getVerb()).thenReturn(RestRequest.Verb.GET);
        when(mockProxyMessage.getRest()).thenReturn(mockRestRequest);
        assertTrue("Wasn't able to handle list methods", handlerToTest.canHandle(serviceId, mockProxyMessage));
    }

    @Test
    public void shouldBeAbleToHandleAllowedMethods() {
        RestMetadataServiceHandlerImpl handlerToTest = new RestMetadataServiceHandlerImpl();
        ServiceId.Conf serviceId = ServiceId.Conf.create(DEFAULT_CLIENT, ALLOWED_METHODS);
        RestRequest mockRestRequest = mock(RestRequest.class);
        when(mockRestRequest.getVerb()).thenReturn(RestRequest.Verb.GET);
        when(mockProxyMessage.getRest()).thenReturn(mockRestRequest);
        assertTrue("Wasn't able to handle allowed methods",
                handlerToTest.canHandle(serviceId, mockProxyMessage));
    }

    @Test
    public void shouldHandleListMethods() throws Exception {

        RestMetadataServiceHandlerImpl handlerToTest = new RestMetadataServiceHandlerImpl();
        ServiceId.Conf serviceId = ServiceId.Conf.create(DEFAULT_CLIENT, LIST_METHODS);

        RestRequest mockRestRequest = mock(RestRequest.class);
        when(mockRestRequest.getServiceId()).thenReturn(serviceId);
        when(mockRestRequest.getVerb()).thenReturn(RestRequest.Verb.GET);
        when(mockRestRequest.getClientId()).thenReturn(DEFAULT_CLIENT);
        when(mockRestRequest.getHash()).thenReturn(REQUEST_HASH);
        when(mockProxyMessage.getRest()).thenReturn(mockRestRequest);

        ProxyMessageDecoder mockDecoder = mock(ProxyMessageDecoder.class);
        ProxyMessageEncoder mockEncoder = mock(ProxyMessageEncoder.class);
        handlerToTest.startHandling(mockRequest, mockProxyMessage, mockDecoder, mockEncoder, httpClientMock,
                httpClientMock, mock(OpMonitoringData.class));

        RestResponse restResponse = handlerToTest.getRestResponse();
        assertEquals(HttpStatus.SC_OK, restResponse.getResponseCode());
        assertEquals("OK", restResponse.getReason());
        CachingStream restResponseBody = handlerToTest.getRestResponseBody();
        MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        RestServiceDetailsListType restServiceDetailsList = MAPPER.readValue(restResponseBody.getCachedContents(),
                RestServiceDetailsListType.class);
        assertEquals(3, restServiceDetailsList.getService().size());
        assertEquals(1, restServiceDetailsList.getService().get(0).getEndpointList().size());
    }

    @Test
    public void shouldHandleAllowedMethods() throws Exception {

        RestMetadataServiceHandlerImpl handlerToTest = new RestMetadataServiceHandlerImpl();
        ServiceId.Conf serviceId = ServiceId.Conf.create(DEFAULT_CLIENT, ALLOWED_METHODS);

        RestRequest mockRestRequest = mock(RestRequest.class);
        when(mockRestRequest.getServiceId()).thenReturn(serviceId);
        when(mockRestRequest.getVerb()).thenReturn(RestRequest.Verb.GET);
        when(mockRestRequest.getClientId()).thenReturn(DEFAULT_CLIENT);
        when(mockRestRequest.getHash()).thenReturn(REQUEST_HASH);
        when(mockProxyMessage.getRest()).thenReturn(mockRestRequest);

        ProxyMessageDecoder mockDecoder = mock(ProxyMessageDecoder.class);
        ProxyMessageEncoder mockEncoder = mock(ProxyMessageEncoder.class);
        handlerToTest.startHandling(mockRequest, mockProxyMessage, mockDecoder, mockEncoder, httpClientMock,
                httpClientMock, mock(OpMonitoringData.class));

        RestResponse restResponse = handlerToTest.getRestResponse();
        assertEquals(HttpStatus.SC_OK, restResponse.getResponseCode());
        assertEquals("OK", restResponse.getReason());
        CachingStream restResponseBody = handlerToTest.getRestResponseBody();
        MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        RestServiceDetailsListType restServiceDetailsList = MAPPER.readValue(restResponseBody.getCachedContents(),
                RestServiceDetailsListType.class);
        assertEquals(3, restServiceDetailsList.getService().size());
        assertEquals(1, restServiceDetailsList.getService().get(0).getEndpointList().size());
    }

    @Test
    public void shouldHandleGetOpenApi() throws Exception {

        RestMetadataServiceHandlerImpl handlerToTest = new RestMetadataServiceHandlerImpl();
        ServiceId.Conf serviceId = ServiceId.Conf.create(DEFAULT_CLIENT, GET_OPENAPI);

        when(mockRequest.getRequestURL()).thenReturn(new StringBuffer("https://securityserver:5500"));

        RestRequest mockRestRequest = mock(RestRequest.class);
        when(mockRestRequest.getQuery()).thenReturn("serviceCode=foobar");
        when(mockRestRequest.getServiceId()).thenReturn(serviceId);
        when(mockRestRequest.getVerb()).thenReturn(RestRequest.Verb.GET);
        when(mockRestRequest.getClientId()).thenReturn(DEFAULT_CLIENT);
        when(mockRestRequest.getHash()).thenReturn(REQUEST_HASH);
        when(mockProxyMessage.getRest()).thenReturn(mockRestRequest);

        ProxyMessageDecoder mockDecoder = mock(ProxyMessageDecoder.class);
        ProxyMessageEncoder mockEncoder = mock(ProxyMessageEncoder.class);
        handlerToTest.startHandling(mockRequest, mockProxyMessage, mockDecoder, mockEncoder, httpClientMock,
                httpClientMock, mock(OpMonitoringData.class));

        RestResponse restResponse = handlerToTest.getRestResponse();
        assertEquals(HttpStatus.SC_OK, restResponse.getResponseCode());
        assertEquals("OK", restResponse.getReason());
        CachingStream restResponseBody = handlerToTest.getRestResponseBody();
        assertTrue(restResponseBody.getCachedContents().size() > 0);
    }

    @Test
    public void shouldOverrideServerUrlsForYaml() throws Exception {
        RestMetadataServiceHandlerImpl handlerToTest = new RestMetadataServiceHandlerImpl();
        ProxyMessageDecoder mockDecoder = mock(ProxyMessageDecoder.class);
        ProxyMessageEncoder mockEncoder = mock(ProxyMessageEncoder.class);

        // Test for petstore.yaml parsing
        ServiceId.Conf serviceId = ServiceId.Conf.create(DEFAULT_CLIENT, GET_OPENAPI);

        RestRequest mockRestRequest = mock(RestRequest.class);
        when(mockRestRequest.getQuery()).thenReturn("serviceCode=yaml");
        when(mockRestRequest.getServiceId()).thenReturn(serviceId);
        when(mockRestRequest.getVerb()).thenReturn(RestRequest.Verb.GET);
        when(mockRestRequest.getClientId()).thenReturn(DEFAULT_CLIENT);
        when(mockRestRequest.getHash()).thenReturn(REQUEST_HASH);
        when(mockProxyMessage.getRest()).thenReturn(mockRestRequest);


        handlerToTest.startHandling(mockRequest, mockProxyMessage, mockDecoder, mockEncoder, httpClientMock,
                httpClientMock, mock(OpMonitoringData.class));

        CachingStream yamlFileResponseBody = handlerToTest.getRestResponseBody();
        String yaml = new BufferedReader(
                new InputStreamReader(yamlFileResponseBody.getCachedContents(), StandardCharsets.UTF_8))
                .lines()
                .collect(Collectors.joining("\n"));

        assertFalse(yaml.contains("http://petstore.swagger.io/v1/cats"));
        assertTrue(yaml.contains("/v1/cats"));
        assertEquals(StringUtils.countMatches(yaml, "/v1/cats"), 1);
        assertTrue(yaml.contains("null"));
        assertEquals(StringUtils.countMatches(yaml, "null"), 2);
        assertTrue(yaml.contains("- \"this\""));
        assertTrue(yaml.contains("- \"should\""));
        assertTrue(yaml.contains("- \"be\""));
        assertTrue(yaml.contains("- \"a string\""));
        assertTrue(yaml.contains("- url: \"\""));
    }

    @Test
    public void shouldOverrideServerUrlsForJson() throws Exception {
        RestMetadataServiceHandlerImpl handlerToTest = new RestMetadataServiceHandlerImpl();
        ProxyMessageDecoder mockDecoder = mock(ProxyMessageDecoder.class);
        ProxyMessageEncoder mockEncoder = mock(ProxyMessageEncoder.class);

        // Test petstore.json parsing
        ServiceId.Conf serviceId = ServiceId.Conf.create(SECONDARY_CLIENT, GET_OPENAPI);

        RestRequest secondaryMockRestRequest = mock(RestRequest.class);
        when(secondaryMockRestRequest.getQuery()).thenReturn("serviceCode=json");
        when(secondaryMockRestRequest.getServiceId()).thenReturn(serviceId);
        when(secondaryMockRestRequest.getVerb()).thenReturn(RestRequest.Verb.GET);
        when(secondaryMockRestRequest.getClientId()).thenReturn(SECONDARY_CLIENT);
        when(secondaryMockRestRequest.getHash()).thenReturn(REQUEST_HASH);
        when(mockProxyMessage.getRest()).thenReturn(secondaryMockRestRequest);

        handlerToTest.startHandling(mockRequest, mockProxyMessage, mockDecoder, mockEncoder, httpClientMock,
                httpClientMock, mock(OpMonitoringData.class));

        CachingStream jsonFileResponseBody = handlerToTest.getRestResponseBody();
        String json = new BufferedReader(
                new InputStreamReader(jsonFileResponseBody.getCachedContents(), StandardCharsets.UTF_8))
                .lines()
                .collect(Collectors.joining("\n"));

        assertFalse(json.contains("https://petstore.swagger.io/v2"));
        assertTrue(json.contains("\"/v2\""));
        assertTrue(json.contains("https://{username}.petstore.swagger.io:{port}/{basePath}"));
    }

    @Test(expected = CodedException.class)
    public void shouldDetectUnsupportedOpenapiVersion() throws Exception {
        RestMetadataServiceHandlerImpl handlerToTest = new RestMetadataServiceHandlerImpl();
        ProxyMessageDecoder mockDecoder = mock(ProxyMessageDecoder.class);
        ProxyMessageEncoder mockEncoder = mock(ProxyMessageEncoder.class);

        // Test for petstore.yaml parsing
        ServiceId.Conf serviceId = ServiceId.Conf.create(CLIENT_WITH_UNSUPPORTED_OPENAPI, GET_OPENAPI);

        RestRequest mockRestRequest = mock(RestRequest.class);
        when(mockRestRequest.getQuery()).thenReturn("serviceCode=yaml");
        when(mockRestRequest.getServiceId()).thenReturn(serviceId);
        when(mockRestRequest.getVerb()).thenReturn(RestRequest.Verb.GET);
        when(mockRestRequest.getClientId()).thenReturn(CLIENT_WITH_UNSUPPORTED_OPENAPI);
        when(mockRestRequest.getHash()).thenReturn(REQUEST_HASH);
        when(mockProxyMessage.getRest()).thenReturn(mockRestRequest);

        handlerToTest.startHandling(mockRequest, mockProxyMessage, mockDecoder, mockEncoder, httpClientMock,
                httpClientMock, mock(OpMonitoringData.class));
    }

}
