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

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.globalconf.MemberInfo;
import ee.ria.xroad.common.identifier.CentralServiceId;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.metadata.CentralServiceListType;
import ee.ria.xroad.common.metadata.ClientListType;
import ee.ria.xroad.common.metadata.ObjectFactory;
import ee.ria.xroad.proxy.conf.KeyConf;
import ee.ria.xroad.proxy.testsuite.TestGlobalConf;
import ee.ria.xroad.proxy.testsuite.TestKeyConf;
import ee.ria.xroad.proxy.util.MetaserviceTestUtil;
import org.junit.*;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.identifier.CentralServiceId.create;
import static ee.ria.xroad.common.metadata.MetadataRequests.*;
import static ee.ria.xroad.proxy.util.MetaserviceTestUtil.xmlUtf8ContentTypes;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit test for {@link MetadataClientRequestProcessor}
 */
public class MetadataClientRequestProcessorTest {

    private static final String EXPECTED_XR_INSTANCE = "EE";


    private static Unmarshaller unmarshaller;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private HttpServletRequest mockRequest;
    private HttpServletResponse mockResponse;
    private MetaserviceTestUtil.StubServletOutputStream mockServletOutputStream;


    /**
     * Init class-wide test instances
     */
    @BeforeClass
    public static void initCommon() throws JAXBException {
        unmarshaller = JAXBContext.newInstance(ObjectFactory.class).createUnmarshaller();
    }

    /**
     * Init data for tests
     */
    @Before
    public void init() throws IOException {

        GlobalConf.reload(new TestGlobalConf());
        KeyConf.reload(new TestKeyConf());

        mockRequest = mock(HttpServletRequest.class);
        mockResponse = mock(HttpServletResponse.class);
        mockServletOutputStream = new MetaserviceTestUtil.StubServletOutputStream();
        when(mockResponse.getOutputStream()).thenReturn(mockServletOutputStream);
    }


    @Test
    public void shouldBeAbleToProcessListClients() {

        MetadataClientRequestProcessor processorToTest =
                new MetadataClientRequestProcessor(LIST_CLIENTS, mockRequest, mockResponse);

        assertTrue("Wasn't able to process list clients", processorToTest.canProcess());
    }

    @Test
    public void shouldBeAbleToProcessListCentralServices() {

        MetadataClientRequestProcessor processorToTest =
                new MetadataClientRequestProcessor(LIST_CENTRAL_SERVICES, mockRequest, mockResponse);

        assertTrue("Wasn't able to process central services", processorToTest.canProcess());
    }

    @Test
    public void shouldBeAbleToProcessGetWsdl() {

        // WSDL GET is enabled/disabled with system property
        // Force it to enabled state
        System.setProperty(SystemProperties.ALLOW_GET_WSDL_REQUEST, "true");

        MetadataClientRequestProcessor processorToTest =
                new MetadataClientRequestProcessor(WSDL, mockRequest, mockResponse);

        assertTrue("Wasn't able to process get wsdl request", processorToTest.canProcess());
    }

    @Test
    public void shouldNotBeAbleToProcessGetWsdl() {

        // WSDL GET is enabled/disabled with system property
        // Force it to disabled state
        System.setProperty(SystemProperties.ALLOW_GET_WSDL_REQUEST, "false");

        MetadataClientRequestProcessor processorToTest =
                new MetadataClientRequestProcessor(WSDL, mockRequest, mockResponse);

        assertFalse("Was able to process get wsdl request", processorToTest.canProcess());
    }

    @Test
    public void shouldNotBeAbleToProcessRandomRequest() {

        MetadataClientRequestProcessor processorToTest =
                new MetadataClientRequestProcessor("getRandom", mockRequest, mockResponse);

        assertFalse("Was able to process a random target", processorToTest.canProcess());
    }

    @Test
    public void shouldProcessListClients() throws Exception {

        final List<MemberInfo> expectedMembers = Arrays.asList(
                createMember("producer", null),
                createMember("producer", "subsystem"),
                createMember("anothermemeber", null),
                createMember("anothermemeber", "somesub"),
                createMember("thirdmember", null));

        GlobalConf.reload(new TestGlobalConf() {

            @Override
            public List<MemberInfo> getMembers(String... instanceIdentifier) {
                String[] instances = instanceIdentifier;
                assertThat("Wrong Xroad instance in query", instances, arrayContaining(EXPECTED_XR_INSTANCE));
                return expectedMembers;
            }

        });

        MetadataClientRequestProcessor processorToTest =
                new MetadataClientRequestProcessor(LIST_CLIENTS, mockRequest, mockResponse);

        processorToTest.process();

        assertContentTypeIsIn(xmlUtf8ContentTypes());

        List<MemberInfo> members = unmarshaller.unmarshal(
                mockServletOutputStream.getResponseSource(), ClientListType.class)
                .getValue()
                .getMember()
                .stream()
                .map(clientType -> new MemberInfo(clientType.getId(), clientType.getName()))
                .collect(Collectors.toList());


        assertThat("Wrong amount of clients",
                members.size(), is(expectedMembers.size()));

        assertThat("Wrong members", members, containsInAnyOrder(expectedMembers.toArray()));

    }

    @Test
    public void shouldProcessListCentralServices() throws Exception {

        final List<CentralServiceId> expectedCentraServices = Arrays.asList(
                create(EXPECTED_XR_INSTANCE, "getInfo"),
                create(EXPECTED_XR_INSTANCE, "someService"),
                create(EXPECTED_XR_INSTANCE, "getRandom"));

        GlobalConf.reload(new TestGlobalConf() {

            @Override
            public List<CentralServiceId> getCentralServices(String instanceIdentifier) {
                assertThat("Wrong Xroad instance in query", instanceIdentifier, is(EXPECTED_XR_INSTANCE));
                return expectedCentraServices;
            }
        });

        MetadataClientRequestProcessor processorToTest =
                new MetadataClientRequestProcessor(LIST_CENTRAL_SERVICES, mockRequest, mockResponse);

        processorToTest.process();

        assertContentTypeIsIn(xmlUtf8ContentTypes());

        List<CentralServiceId> resultCentralServices = unmarshaller.unmarshal(
                mockServletOutputStream.getResponseSource(), CentralServiceListType.class)
                .getValue().getCentralService();


        assertThat("Wrong amount of services",
                resultCentralServices.size(), is(expectedCentraServices.size()));

        assertThat("Wrong services", resultCentralServices,
                containsInAnyOrder(expectedCentraServices.toArray()));

    }

    // handle WSDL does not have it's own unit test in this class, but WsdlRequestProcessor has it's own test, and it
    // has an integration test. A new test here would test that processor.processor() triggers processor.handleWsdl()

    private void assertContentTypeIsIn(List<String> allowedContentTypes) {
        ArgumentCaptor<String> contentTypeCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockResponse).setContentType(contentTypeCaptor.capture());
        assertThat("Wrong content type", contentTypeCaptor.getValue(), isIn(allowedContentTypes));
    }

    private static MemberInfo createMember(String member, String subsystem) {
        return new MemberInfo(ClientId.create(EXPECTED_XR_INSTANCE, "BUSINESS",
                member, subsystem), member + "-name");
    }


}
