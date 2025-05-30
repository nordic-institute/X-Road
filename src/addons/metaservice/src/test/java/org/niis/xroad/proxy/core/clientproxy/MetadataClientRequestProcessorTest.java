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
package org.niis.xroad.proxy.core.clientproxy;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.metadata.ClientListType;
import ee.ria.xroad.common.metadata.ObjectFactory;
import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.ResponseWrapper;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpURI;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.impl.cert.CertChainFactory;
import org.niis.xroad.globalconf.model.MemberInfo;
import org.niis.xroad.keyconf.KeyConfProvider;
import org.niis.xroad.proxy.core.test.MetaserviceTestUtil;
import org.niis.xroad.proxy.core.test.TestSuiteGlobalConf;
import org.niis.xroad.proxy.core.test.TestSuiteKeyConf;
import org.niis.xroad.proxy.core.util.CommonBeanProxy;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.in;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.proxy.core.test.MetaserviceTestUtil.xmlUtf8ContentTypes;
import static org.niis.xroad.proxy.core.util.MetadataRequests.LIST_CLIENTS;

/**
 * Unit test for {@link MetadataClientRequestProcessor}
 */
public class MetadataClientRequestProcessorTest {

    private static final String EXPECTED_XR_INSTANCE = "EE";

    private static Unmarshaller unmarshaller;

    private RequestWrapper mockRequest;
    private RequestWrapper mockJsonRequest;
    private ResponseWrapper mockResponse;
    private MetaserviceTestUtil.StubServletOutputStream mockServletOutputStream;

    private CommonBeanProxy commonBeanProxy;
    private GlobalConfProvider globalConfProvider;
    private KeyConfProvider keyConfProvider;
    private ServerConfProvider serverConfProvider;
    private CertChainFactory certChainFactory;

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

        globalConfProvider = new TestSuiteGlobalConf();
        keyConfProvider = new TestSuiteKeyConf(globalConfProvider);
        serverConfProvider = mock(ServerConfProvider.class);
        certChainFactory = mock(CertChainFactory.class);

        commonBeanProxy = new CommonBeanProxy(globalConfProvider, serverConfProvider, keyConfProvider,
                null, certChainFactory, null);
        mockRequest = mock(RequestWrapper.class);
        mockJsonRequest = mock(RequestWrapper.class);
        mockResponse = mock(ResponseWrapper.class);
        mockServletOutputStream = new MetaserviceTestUtil.StubServletOutputStream();
        var mockHeaders = mock(HttpFields.class);
        var mockHttpUri = mock(HttpURI.class);
        when(mockJsonRequest.getHeaders()).thenReturn(mockHeaders);
        when(mockJsonRequest.getHttpURI()).thenReturn(mockHttpUri);
        when(mockHeaders.getValues("Accept"))
                .thenReturn(Collections.enumeration(List.of("application/json")));
    }


    @Test
    public void shouldBeAbleToProcessListClients() {

        MetadataClientRequestProcessor processorToTest =
                new MetadataClientRequestProcessor(commonBeanProxy,
                        LIST_CLIENTS, mockRequest, mockResponse);

        assertTrue("Wasn't able to process list clients", processorToTest.canProcess());
    }

    @Test
    public void shouldNotBeAbleToProcessRandomRequest() {

        MetadataClientRequestProcessor processorToTest =
                new MetadataClientRequestProcessor(commonBeanProxy, "getRandom", mockRequest, mockResponse);

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

        globalConfProvider = new TestSuiteGlobalConf() {

            @Override
            public List<MemberInfo> getMembers(String... instanceIdentifier) {
                assertThat("Wrong Xroad instance in query", instanceIdentifier, arrayContaining(EXPECTED_XR_INSTANCE));
                return expectedMembers;
            }

        };
        commonBeanProxy = new CommonBeanProxy(globalConfProvider, serverConfProvider, keyConfProvider,
                null, certChainFactory, null);

        var mockHeaders = mock(HttpFields.class);
        var mockHttpUri = mock(HttpURI.class);
        when(mockRequest.getHeaders()).thenReturn(mockHeaders);
        when(mockRequest.getHttpURI()).thenReturn(mockHttpUri);

        MetadataClientRequestProcessor processorToTest =
                new MetadataClientRequestProcessor(commonBeanProxy,
                        LIST_CLIENTS, mockRequest, mockResponse);

        when(mockRequest.getParametersMap()).thenReturn(Map.of());
        when(mockResponse.getOutputStream()).thenReturn(mockServletOutputStream);
        processorToTest.process();

        assertContentTypeIsIn(xmlUtf8ContentTypes());

        List<MemberInfo> members = unmarshaller.unmarshal(
                        mockServletOutputStream.getResponseSource(), ClientListType.class)
                .getValue()
                .getMember()
                .stream()
                .map(clientType -> new MemberInfo(clientType.getId(), clientType.getName(), clientType.getSubsystemName()))
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

        globalConfProvider = new TestSuiteGlobalConf() {
            @Override
            public List<MemberInfo> getMembers(String... instanceIdentifier) {
                assertThat("Wrong Xroad instance in query", instanceIdentifier, arrayContaining(EXPECTED_XR_INSTANCE));
                return expectedMembers;
            }
        };
        commonBeanProxy = new CommonBeanProxy(globalConfProvider, serverConfProvider, keyConfProvider,
                null, certChainFactory, null);

        MetadataClientRequestProcessor processorToTest =
                new MetadataClientRequestProcessor(commonBeanProxy,
                        LIST_CLIENTS, mockJsonRequest, mockResponse);

        when(mockJsonRequest.getParametersMap()).thenReturn(Map.of());
        when(mockResponse.getOutputStream()).thenReturn(mockServletOutputStream);

        processorToTest.process();

        assertContentTypeIsIn(List.of("application/json; charset=utf-8"));

        assertThatJson(new String(mockServletOutputStream.getAsBytes(), UTF_8))
                .isEqualTo("""
                        {
                            "member": [
                                {
                                    "id": {
                                        "member_class": "BUSINESS",
                                        "member_code": "producer",
                                        "object_type": "MEMBER",
                                        "xroad_instance": "EE"
                                    },
                                    "name": "producer-name"
                                },
                                {
                                    "id": {
                                        "member_class": "BUSINESS",
                                        "member_code": "producer",
                                        "object_type": "SUBSYSTEM",
                                        "subsystem_code": "subsystem",
                                        "xroad_instance": "EE"
                                    },
                                    "name": "producer-name",
                                    "subsystem_name": "subsystem-name"
                                }
                            ]
                        }""");
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
        verify(mockResponse).setContentType(contentTypeCaptor.capture());
        assertThat("Wrong content type", contentTypeCaptor.getValue(), is(in(allowedContentTypes)));
    }

    private static MemberInfo createMember(String member, String subsystem) {
        return new MemberInfo(ClientId.Conf.create(EXPECTED_XR_INSTANCE, "BUSINESS",
                member, subsystem), member + "-name", subsystem == null ? null : (subsystem + "-name"));
    }


}
