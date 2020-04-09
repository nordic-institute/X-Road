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
package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.identifier.CentralServiceId;
import ee.ria.xroad.common.metadata.CentralServiceListType;
import ee.ria.xroad.common.metadata.MetadataRequests;
import ee.ria.xroad.common.metadata.ObjectFactory;
import ee.ria.xroad.common.util.AbstractHttpSender;
import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;
import ee.ria.xroad.proxy.testsuite.TestSuiteGlobalConf;
import ee.ria.xroad.proxy.util.MetaserviceTestUtil;

import org.apache.http.client.utils.URIBuilder;
import org.apache.james.mime4j.stream.BodyDescriptor;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;

import static ee.ria.xroad.common.identifier.CentralServiceId.create;
import static ee.ria.xroad.proxy.util.MetaserviceTestUtil.DUMMY_QUERY_FILE;
import static ee.ria.xroad.proxy.util.MetaserviceTestUtil.PARAM_INSTANCE_IDENTIFIER;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.isIn;
import static org.junit.Assert.assertThat;

/**
 * Test central services list retrieval
 * Result: client receives a list of central services.
 */
public class GetListCentralServicesMessage extends MessageTestCase {

    private static final String EXPECTED_XR_INSTANCE = "EE";
    private static final List<CentralServiceId> EXPECTED_CENTRAL_SERVICES = Arrays.asList(
            create(EXPECTED_XR_INSTANCE, "getInfo"),
            create(EXPECTED_XR_INSTANCE, "someService"),
            create(EXPECTED_XR_INSTANCE, "getRandom"));

    /**
     * Constructs the test case.
     */
    public GetListCentralServicesMessage() {
        this.requestFileName = DUMMY_QUERY_FILE;
        this.httpMethod = "GET";
    }

    @Override
    protected URI getClientUri() throws URISyntaxException {
        return new URIBuilder()
                .setScheme("http").setHost("localhost")
                .setPort(SystemProperties.getClientProxyHttpPort())
                .setPath(MetadataRequests.LIST_CENTRAL_SERVICES)
                .setParameter(PARAM_INSTANCE_IDENTIFIER, EXPECTED_XR_INSTANCE)
                .build();
    }

    @Override
    protected void validateNormalResponse(Message receivedResponse)
            throws Exception {

        CentralServicesMessage message = (CentralServicesMessage) receivedResponse.parse();
        List<CentralServiceId> resultCentralServices = message.getCentralServiceListType().getCentralService();

        List<String> expectedContentTypes = MetaserviceTestUtil.xmlUtf8ContentTypes();

        assertThat("Wrong content type", receivedResponse.getContentType(), isIn(expectedContentTypes));


        assertThat("Wrong amount of services",
                resultCentralServices.size(), is(EXPECTED_CENTRAL_SERVICES.size()));

        assertThat("Wrong services", resultCentralServices,
                containsInAnyOrder(EXPECTED_CENTRAL_SERVICES.toArray()));

    }

    @Override
    protected void startUp() throws Exception {
        super.startUp();

        GlobalConf.reload(new TestSuiteGlobalConf() {

            @Override
            public List<CentralServiceId> getCentralServices(String instanceIdentifier) {
                assertThat("Wrong Xroad instance in query", instanceIdentifier, is(EXPECTED_XR_INSTANCE));
                return EXPECTED_CENTRAL_SERVICES;
            }
        });

    }

    @Override
    protected Message extractResponse(AbstractHttpSender sender) throws Exception {
        return new CentralServicesMessage(sender);
    }

    private static class CentralServicesMessage extends Message {


        private CentralServiceListType centralServiceListType;

        CentralServicesMessage(AbstractHttpSender sender) throws Exception {
            super(sender.getResponseContent(), sender.getResponseContentType());
            this.parser.setContentHandler(new CentralServicesMessageHandler());
        }

        @Override
        public boolean isResponse() {
            return true;
        }


        public CentralServiceListType getCentralServiceListType() {
            return centralServiceListType;
        }

        private static class CentralServicesMessageException extends IOException {

            CentralServicesMessageException(String message, Throwable cause) {
                super(message, cause);
            }
        }

        protected class CentralServicesMessageHandler extends MessageContentHandler {

            private Unmarshaller unmarshaller;


            CentralServicesMessageHandler() throws CentralServicesMessageException {
                try {
                    unmarshaller = JAXBContext.newInstance(ObjectFactory.class).createUnmarshaller();
                } catch (JAXBException e) {
                    throw new CentralServicesMessageException("Could not create unmarshaller for test", e);
                }
            }

            @Override
            public void body(BodyDescriptor bd, InputStream is) throws CentralServicesMessageException {

                try {
                    JAXBElement<CentralServiceListType> element = unmarshaller.unmarshal(
                            new StreamSource(is), CentralServiceListType.class);
                    centralServiceListType = element.getValue();
                } catch (JAXBException e) {
                    throw new CentralServicesMessageException("Parsing list central services response failed.", e);
                }
            }

        }
    }
}
