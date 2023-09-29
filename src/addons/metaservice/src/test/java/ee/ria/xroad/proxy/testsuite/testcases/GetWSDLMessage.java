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
package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.common.conf.serverconf.IsAuthentication;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.conf.serverconf.model.DescriptionType;
import ee.ria.xroad.common.conf.serverconf.model.ServerConfType;
import ee.ria.xroad.common.conf.serverconf.model.ServiceDescriptionType;
import ee.ria.xroad.common.conf.serverconf.model.ServiceType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.AbstractHttpSender;
import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;
import ee.ria.xroad.proxy.testsuite.TestSuiteServerConf;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.apache.james.mime4j.stream.BodyDescriptor;
import org.xml.sax.InputSource;

import javax.wsdl.Definition;
import javax.wsdl.WSDLException;
import javax.wsdl.factory.WSDLFactory;
import javax.wsdl.xml.WSDLReader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static ee.ria.xroad.common.conf.serverconf.ServerConfDatabaseCtx.doInTransaction;
import static ee.ria.xroad.proxy.util.MetaserviceTestUtil.cleanDB;
import static ee.ria.xroad.proxy.util.MetaserviceTestUtil.parseOperationNamesFromWSDLDefinition;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThat;

/**
 * Test WSDL retrieval.
 * Result: client receives the WSDL of the given service.
 */
public class GetWSDLMessage extends MessageTestCase {

    private static final int WSDL_SERVER_PORT = 9857;
    private static final String EXPECTED_WSDL_QUERY_PATH = "/wsdlMock";

    // the uri from which the WSDL can be found by the meta service
    private static final String MOCK_SERVER_WSDL_URL =
            "http://localhost:" + WSDL_SERVER_PORT + EXPECTED_WSDL_QUERY_PATH;
    // file that the mock server serves as the WSDL, found under resources/__files
    private static final String MOCK_SERVER_WSDL_FILE = "wsdl.wsdl";


    private final WireMockServer mockServer;

    private final ClientId.Conf expectedProviderQuery =
            ClientId.Conf.create("EE", "BUSINESS", "producer");

    private final String expectedServiceNameForWSDLQuery = "getRandom";
    private final List<String> expectedWSDLServiceNames =
            Arrays.asList(expectedServiceNameForWSDLQuery, "helloService");


    /**
     * Constructs the test case.
     */
    public GetWSDLMessage() {
        this.requestFileName = "getWsdl.query";
        this.mockServer = new WireMockServer(options().port(WSDL_SERVER_PORT));
    }

    @Override
    protected void validateNormalResponse(Message receivedResponse) {

        if (!(receivedResponse instanceof WSDLMessage)) {
            throw new IllegalStateException("Needed a WSDL response");
        }

        WSDLMessage wsdl = (WSDLMessage) receivedResponse;
        wsdl.parse();

        Definition definition = wsdl.getDefinition();

        List<String> operationNames = parseOperationNamesFromWSDLDefinition(definition);

        assertThat(wsdl.getContentType(), startsWith("multipart/related; type=\"text/xml\"; charset=UTF-8;"));

        assertThat("Expected to find certain operations",
                operationNames,
                containsInAnyOrder(expectedWSDLServiceNames.toArray()));

    }

    @Override
    protected Message extractResponse(AbstractHttpSender sender) throws Exception {
        return new WSDLMessage(sender);
    }

    @Override
    protected void startUp() throws Exception {
        super.startUp();
        ServerConf.reload(new TestSuiteServerConf() {
            @Override
            public IsAuthentication getIsAuthentication(ClientId client) {
                return  IsAuthentication.NOSSL;
            }
        });
        setUpDatabase();

        mockServer.stubFor(WireMock.any(urlPathEqualTo(EXPECTED_WSDL_QUERY_PATH))
                .willReturn(aResponse().withBodyFile(MOCK_SERVER_WSDL_FILE)));
        mockServer.start();
    }

    private void setUpDatabase() throws Exception {
        ServerConfType conf = new ServerConfType();
        conf.setServerCode("TestServer");

        ClientType client = new ClientType();
        client.setConf(conf);

        conf.getClient().add(client);

        client.setIdentifier(expectedProviderQuery);

        ServiceDescriptionType wsdl = new ServiceDescriptionType();
        wsdl.setClient(client);
        wsdl.setUrl(MOCK_SERVER_WSDL_URL);
        wsdl.setType(DescriptionType.WSDL);

        ServiceType service = new ServiceType();
        service.setServiceDescription(wsdl);
        service.setTitle("getRandomTitle");
        service.setServiceCode(expectedServiceNameForWSDLQuery);

        wsdl.getService().add(service);

        client.getServiceDescription().add(wsdl);

        doInTransaction(session -> {
            session.save(conf);
            return null;
        });

    }

    @Override
    protected void closeDown() throws Exception {
        super.closeDown();
        mockServer.stop();
        cleanDB();
    }

    private static class WSDLMessage extends Message {

        private Definition definition;

        WSDLMessage(AbstractHttpSender sender) throws Exception {
            super(sender.getResponseContent(), sender.getResponseContentType());
            this.parser.setContentHandler(new WSDLMessageHandler());
        }

        Definition getDefinition() {
            return definition;
        }

        @Override
        public boolean isResponse() {
            return true;
        }

        protected class WSDLMessageHandler extends MessageContentHandler {
            private int nextPart = 0;
            private WSDLReader wsdlReader;

            WSDLMessageHandler() throws WSDLException {
                wsdlReader = WSDLFactory.newInstance().newWSDLReader();
            }

            @Override
            public void body(BodyDescriptor bd, InputStream is) throws IOException {

                switch (nextPart) {
                    case 0: // SOAP
                        nextPart = 1;
                        break;
                    default: // Attachment => WSDL
                        try {
                            definition = wsdlReader.readWSDL(null, new InputSource(is));
                        } catch (WSDLException e) {
                            throw new IOException(e);

                        }
                        break;
                }
            }
        }

    }

}
