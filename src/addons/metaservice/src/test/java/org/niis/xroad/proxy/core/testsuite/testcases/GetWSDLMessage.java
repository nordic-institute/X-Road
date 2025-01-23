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
package org.niis.xroad.proxy.core.testsuite.testcases;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.AbstractHttpSender;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.apache.james.mime4j.stream.BodyDescriptor;
import org.niis.xroad.proxy.application.testsuite.Message;
import org.niis.xroad.proxy.application.testsuite.MessageTestCase;
import org.niis.xroad.proxy.application.testsuite.TestSuiteServerConf;
import org.niis.xroad.serverconf.IsAuthentication;
import org.niis.xroad.serverconf.model.ClientType;
import org.niis.xroad.serverconf.model.DescriptionType;
import org.niis.xroad.serverconf.model.ServerConfType;
import org.niis.xroad.serverconf.model.ServiceDescriptionType;
import org.niis.xroad.serverconf.model.ServiceType;
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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.startsWith;
import static org.niis.xroad.proxy.core.util.MetaserviceTestUtil.cleanDB;
import static org.niis.xroad.proxy.core.util.MetaserviceTestUtil.parseOperationNamesFromWSDLDefinition;
import static org.niis.xroad.serverconf.impl.ServerConfDatabaseCtx.doInTransaction;

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

        if (!(receivedResponse instanceof WSDLMessage wsdl)) {
            throw new IllegalStateException("Needed a WSDL response");
        }

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
        serverConfProvider.setServerConfProvider(new TestSuiteServerConf() {
            @Override
            public IsAuthentication getIsAuthentication(ClientId client) {
                return IsAuthentication.NOSSL;
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
            session.persist(conf);
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
                if (nextPart == 0) {
                    // SOAP
                    nextPart = 1;
                } else {
                    // Attachment => WSDL
                    try {
                        definition = wsdlReader.readWSDL(null, new InputSource(is));
                    } catch (WSDLException e) {
                        throw new IOException(e);

                    }
                }
            }
        }

    }

}
