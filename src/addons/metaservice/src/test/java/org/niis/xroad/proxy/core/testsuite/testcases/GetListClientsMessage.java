/*
 * The MIT License
 *
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
package org.niis.xroad.proxy.core.testsuite.testcases;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.metadata.ClientListType;
import ee.ria.xroad.common.metadata.ClientType;
import ee.ria.xroad.common.metadata.ObjectFactory;
import ee.ria.xroad.common.util.AbstractHttpSender;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import org.apache.http.client.utils.URIBuilder;
import org.apache.james.mime4j.stream.BodyDescriptor;
import org.niis.xroad.globalconf.model.MemberInfo;
import org.niis.xroad.proxy.core.test.Message;
import org.niis.xroad.proxy.core.test.MessageTestCase;
import org.niis.xroad.proxy.core.test.MetaserviceTestUtil;
import org.niis.xroad.proxy.core.test.TestSuiteGlobalConf;
import org.niis.xroad.proxy.core.util.MetadataRequests;

import javax.xml.transform.stream.StreamSource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.in;
import static org.niis.xroad.proxy.core.test.MetaserviceTestUtil.DUMMY_QUERY_FILE;
import static org.niis.xroad.proxy.core.test.MetaserviceTestUtil.PARAM_INSTANCE_IDENTIFIER;

/**
 * Test member list retrieval
 * Result: client receives a list of members.
 */
public class GetListClientsMessage extends MessageTestCase {

    private static final String EXPECTED_XR_INSTANCE = "EE";

    /**
     * Constructs the test case.
     */
    public GetListClientsMessage() {
        this.requestFileName = DUMMY_QUERY_FILE;
        this.httpMethod = "GET";
    }

    private List<MemberInfo> expectedMembers = Arrays.asList(
            createMember("producer", null),
            createMember("producer", "subsystem"),
            createMember("anothermemeber", null),
            createMember("anothermemeber", "somesub"),
            createMember("thirdmember", null));


    @Override
    protected URI getClientUri() throws URISyntaxException {
        return new URIBuilder()
                .setScheme("http").setHost("localhost")
                .setPort(SystemProperties.getClientProxyHttpPort())
                .setPath(MetadataRequests.LIST_CLIENTS)
                .setParameter(PARAM_INSTANCE_IDENTIFIER, EXPECTED_XR_INSTANCE)
                .build();
    }

    @Override
    protected void validateNormalResponse(Message receivedResponse)
            throws Exception {

        ClientListMessage message = (ClientListMessage) receivedResponse.parse();
        List<ClientType> clientListType = message.getClientListType().getMember();

        // the content type might arrive without the space,
        // even though the MetadataServiceHandler uses the same MimeUtils value
        List<String> expectedContentTypes = MetaserviceTestUtil.xmlUtf8ContentTypes();

        assertThat("Wrong content type", receivedResponse.getContentType(), is(in(expectedContentTypes)));

        List<MemberInfo> resultMembers = clientListType
                .stream()
                .map(clientType -> new MemberInfo(clientType.getId(), clientType.getName(), clientType.getSubsystemName()))
                .collect(Collectors.toList());

        assertThat("Wrong amount of clients",
                resultMembers.size(), is(expectedMembers.size()));

        assertThat("Wrong members", resultMembers, containsInAnyOrder(expectedMembers.toArray()));

    }

    @Override
    protected void startUp() throws Exception {
        super.startUp();

        globalConfProvider.setGlobalConfProvider(new TestSuiteGlobalConf() {

            @Override
            public List<MemberInfo> getMembers(String... instanceIdentifier) {
                assertThat("Wrong Xroad instance in query", instanceIdentifier, arrayContaining(EXPECTED_XR_INSTANCE));
                return expectedMembers;
            }

        });

    }


    private static MemberInfo createMember(String member, String subsystem) {
        return new MemberInfo(ClientId.Conf.create(EXPECTED_XR_INSTANCE, "BUSINESS",
                member, subsystem), member + "-name", subsystem == null ? null : (subsystem + "-name"));
    }

    @Override
    protected Message extractResponse(AbstractHttpSender sender) throws Exception {
        return new ClientListMessage(sender);
    }

    private static class ClientListMessage extends Message {


        private ClientListType clientListType;

        ClientListMessage(AbstractHttpSender sender) throws Exception {
            super(sender.getResponseContent(), sender.getResponseContentType());
            this.parser.setContentHandler(new ClientListMessageHandler());
        }

        @Override
        public boolean isResponse() {
            return true;
        }


        public ClientListType getClientListType() {
            return clientListType;
        }

        protected class ClientListMessageHandler extends MessageContentHandler {

            private Unmarshaller unmarshaller;


            ClientListMessageHandler() throws ClientListMessageException {
                try {
                    unmarshaller = JAXBContext.newInstance(ObjectFactory.class).createUnmarshaller();
                } catch (JAXBException e) {
                    throw new ClientListMessageException("Could not create unmarshaller for test", e);
                }
            }

            @Override
            public void body(BodyDescriptor bd, InputStream is) throws ClientListMessageException {

                try {
                    JAXBElement<ClientListType> element = unmarshaller.unmarshal(
                            new StreamSource(is), ClientListType.class);
                    clientListType = element.getValue();
                } catch (JAXBException e) {
                    throw new ClientListMessageException("Parsing list clients response failed.", e);
                }
            }

        }


        private static class ClientListMessageException extends IOException {

            ClientListMessageException(String message, Throwable cause) {
                super(message, cause);
            }
        }
    }
}
