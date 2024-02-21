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

import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.globalconf.MemberInfo;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.metadata.ClientListType;
import ee.ria.xroad.common.metadata.ObjectFactory;
import ee.ria.xroad.proxy.conf.KeyConf;
import ee.ria.xroad.proxy.testsuite.TestSuiteGlobalConf;
import ee.ria.xroad.proxy.testsuite.TestSuiteKeyConf;
import ee.ria.xroad.proxy.util.MetaserviceTestUtil;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.io.Content.Sink;
import org.eclipse.jetty.server.ConnectionMetaData;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Fields;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Collectors;

import static ee.ria.xroad.proxy.util.MetadataRequests.LIST_CLIENTS;
import static ee.ria.xroad.proxy.util.MetaserviceTestUtil.xmlUtf8ContentTypes;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.in;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link MetadataClientRequestProcessor}
 */
public class MetadataClientRequestProcessorTest {

    private static final String EXPECTED_XR_INSTANCE = "EE";

    private static Unmarshaller unmarshaller;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private Request mockRequest;
    private Request mockJsonRequest;
    private Response mockResponse;
    private HttpFields.Mutable mockResponseHeaders;
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
    public void init() {

        GlobalConf.reload(new TestSuiteGlobalConf());
        KeyConf.reload(new TestSuiteKeyConf());

        mockRequest = mock(Request.class);
        mockJsonRequest = mock(Request.class);
        mockResponse = mock(Response.class);
        mockResponseHeaders = mock(HttpFields.Mutable.class);
        mockServletOutputStream = new MetaserviceTestUtil.StubServletOutputStream();
        when(mockResponse.getHeaders()).thenReturn(mockResponseHeaders);
        var mockHeaders = mock(HttpFields.class);
        var mockHttpUri = mock(HttpURI.class);
        var connectionMetaData = mock(ConnectionMetaData.class);
        when(mockJsonRequest.getHeaders()).thenReturn(mockHeaders);
        when(mockJsonRequest.getHttpURI()).thenReturn(mockHttpUri);
        when(mockJsonRequest.getConnectionMetaData()).thenReturn(connectionMetaData);
        when(mockHeaders.getValues("Accept"))
                .thenReturn(Collections.enumeration(List.of("application/json")));
    }


    @Test
    public void shouldBeAbleToProcessListClients() {

        MetadataClientRequestProcessor processorToTest =
                new MetadataClientRequestProcessor(LIST_CLIENTS, mockRequest, mockResponse);

        assertTrue("Wasn't able to process list clients", processorToTest.canProcess());
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

        GlobalConf.reload(new TestSuiteGlobalConf() {

            @Override
            public List<MemberInfo> getMembers(String... instanceIdentifier) {
                assertThat("Wrong Xroad instance in query", instanceIdentifier, arrayContaining(EXPECTED_XR_INSTANCE));
                return expectedMembers;
            }

        });

        var mockHeaders = mock(HttpFields.class);
        var mockHttpUri = mock(HttpURI.class);
        var connectionMetaData = mock(ConnectionMetaData.class);
        when(mockRequest.getHeaders()).thenReturn(mockHeaders);
        when(mockRequest.getHttpURI()).thenReturn(mockHttpUri);
        when(mockRequest.getConnectionMetaData()).thenReturn(connectionMetaData);

        MetadataClientRequestProcessor processorToTest =
                new MetadataClientRequestProcessor(LIST_CLIENTS, mockRequest, mockResponse);
        try (
                var mRequest = mockStatic(Request.class);
                var mSink = mockStatic(Sink.class);
        ) {
            var mockFields = mock(Fields.class);
            mRequest.when(() -> Request.getParameters(mockRequest)).thenReturn(mockFields);
            mSink.when(() -> Sink.asOutputStream(mockResponse)).thenReturn(mockServletOutputStream);
            processorToTest.process();
        }
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
    public void shouldProcessListClientsAndReturnJson() throws Exception {

        final List<MemberInfo> expectedMembers = Arrays.asList(
                createMember("producer", null),
                createMember("producer", "subsystem"));

        GlobalConf.reload(new TestSuiteGlobalConf() {
            @Override
            public List<MemberInfo> getMembers(String... instanceIdentifier) {
                assertThat("Wrong Xroad instance in query", instanceIdentifier, arrayContaining(EXPECTED_XR_INSTANCE));
                return expectedMembers;
            }
        });

        MetadataClientRequestProcessor processorToTest =
                new MetadataClientRequestProcessor(LIST_CLIENTS, mockJsonRequest, mockResponse);
        try (
                var mRequest = mockStatic(Request.class);
                var mSink = mockStatic(Sink.class);
        ) {
            var mockFields = mock(Fields.class);
            mRequest.when(() -> Request.getParameters(mockJsonRequest)).thenReturn(mockFields);
            mSink.when(() -> Sink.asOutputStream(mockResponse)).thenReturn(mockServletOutputStream);

            processorToTest.process();
        }
        assertContentTypeIsIn(List.of("application/json; charset=utf-8"));
    }

    @Test
    public void shouldAcceptJson() {
        final Enumeration<String> accept =
                Collections.enumeration(Arrays.asList("text/xml;q=1.0", "application/json;q=0.9 , text/*"));
        assertTrue(MetadataClientRequestProcessor.acceptsJson(accept));
    }

    @Test
    public void shouldNotAcceptJson() {
        assertFalse(MetadataClientRequestProcessor.acceptsJson(null));
        assertFalse(MetadataClientRequestProcessor.acceptsJson(Collections.emptyEnumeration()));

        assertFalse(MetadataClientRequestProcessor.acceptsJson(Collections.enumeration(Arrays.asList(
                        "x-this/that;q=1.0;param=value",
                        "text/xml, */*"
                )))
        );
    }

    // handle WSDL does not have it's own unit test in this class, but WsdlRequestProcessor has it's own test, and it
    // has an integration test. A new test here would test that processor.processor() triggers processor.handleWsdl()

    private void assertContentTypeIsIn(List<String> allowedContentTypes) {
        ArgumentCaptor<String> contentTypeCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockResponseHeaders).put(eq(HttpHeader.CONTENT_TYPE), contentTypeCaptor.capture());
        assertThat("Wrong content type", contentTypeCaptor.getValue(), is(in(allowedContentTypes)));
    }

    private static MemberInfo createMember(String member, String subsystem) {
        return new MemberInfo(ClientId.Conf.create(EXPECTED_XR_INSTANCE, "BUSINESS",
                member, subsystem), member + "-name");
    }


}
