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
package ee.ria.xroad.proxy.testsuite.testcases;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.serverconf.IsAuthentication;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.conf.serverconf.model.ServerConfType;
import ee.ria.xroad.common.conf.serverconf.model.ServiceType;
import ee.ria.xroad.common.conf.serverconf.model.WsdlType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.metadata.MetadataRequests;
import ee.ria.xroad.common.util.AbstractHttpSender;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.SslMessageTestCase;
import ee.ria.xroad.proxy.testsuite.TestServerConf;
import org.apache.http.client.utils.URIBuilder;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.stream.BodyDescriptor;
import org.xml.sax.InputSource;

import javax.wsdl.Definition;
import javax.wsdl.WSDLException;
import javax.wsdl.factory.WSDLFactory;
import javax.wsdl.xml.WSDLReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static ee.ria.xroad.common.conf.serverconf.ServerConfDatabaseCtx.doInTransaction;
import static ee.ria.xroad.proxy.clientproxy.WsdlRequestProcessor.PARAM_INSTANCE_IDENTIFIER;
import static ee.ria.xroad.proxy.clientproxy.WsdlRequestProcessor.PARAM_MEMBER_CLASS;
import static ee.ria.xroad.proxy.clientproxy.WsdlRequestProcessor.PARAM_MEMBER_CODE;
import static ee.ria.xroad.proxy.clientproxy.WsdlRequestProcessor.PARAM_SERVICE_CODE;
import static ee.ria.xroad.proxy.util.MetaserviceTestUtil.DUMMY_QUERY_FILE;
import static ee.ria.xroad.proxy.util.MetaserviceTestUtil.cleanDB;
import static ee.ria.xroad.proxy.util.MetaserviceTestUtil.parseOperationNamesFromWSDLDefinition;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

/**
 * Test WSDL retrieval.
 * Result: client receives the WSDL of the given service.
 */
public class GetWSDLMessage extends SslMessageTestCase {

    private static final int WSDL_SERVER_PORT = 9857;
    private static final String EXPECTED_WSDL_QUERY_PATH = "/wsdlMock";

    // the uri from which the WSDL can be found by the meta service
    private static final String MOCK_SERVER_WSDL_URL =
            "http://localhost:" + WSDL_SERVER_PORT + EXPECTED_WSDL_QUERY_PATH;
    // file that the mock server serves as the WSDL, found under resources/__files
    private static final String MOCK_SERVER_WSDL_FILE = "wsdl.wsdl";


    private final WireMockServer mockServer;

    private final ClientId expectedProviderQuery =
            ClientId.create("EE", "BUSINESS", "producer");

    private final String expectedServiceNameForWSDLQuery = "getRandom";
    private final List<String> expectedWSDLServiceNames =
            Arrays.asList(expectedServiceNameForWSDLQuery, "helloService");


    /**
     * Constructs the test case.
     */
    public GetWSDLMessage() {
        // the contents of this file are not used for testing, MessageTestCase just needs it to be valid SOAP
        this.requestFileName = DUMMY_QUERY_FILE;
        this.httpMethod = "GET";
        this.mockServer = new WireMockServer(options().port(WSDL_SERVER_PORT));
    }

    @Override
    protected URI getClientUri() throws URISyntaxException {
        URIBuilder builder = new URIBuilder();
        builder.setScheme("https").setHost("localhost")
                .setPort(SystemProperties.getClientProxyHttpsPort())
                .setPath(MetadataRequests.WSDL)
                .setParameter(PARAM_INSTANCE_IDENTIFIER, expectedProviderQuery.getXRoadInstance())
                .setParameter(PARAM_MEMBER_CLASS, expectedProviderQuery.getMemberClass())
                .setParameter(PARAM_MEMBER_CODE, expectedProviderQuery.getMemberCode())
                .setParameter(PARAM_SERVICE_CODE, expectedServiceNameForWSDLQuery);
        return builder.build();
    }

    @Override
    protected void validateNormalResponse(Message receivedResponse)
            throws Exception {

        if (!(receivedResponse instanceof WSDLMessage)) {
            throw new IllegalStateException("Needed a WSDL response");
        }

        WSDLMessage wsdl = (WSDLMessage) receivedResponse;
        wsdl.parse();

        Definition definition = wsdl.getDefinition();

        List<String> operationNames = parseOperationNamesFromWSDLDefinition(definition);

        assertThat(wsdl.getContentType(), is(MimeTypes.TEXT_XML));

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
        ServerConf.reload(new TestServerConf() {
            @Override
            public IsAuthentication getIsAuthentication(ClientId client) {
                return  IsAuthentication.SSLAUTH;
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

        WsdlType wsdl = new WsdlType();
        wsdl.setClient(client);
        wsdl.setUrl(MOCK_SERVER_WSDL_URL);
        wsdl.setWsdlLocation("wsdlLocation");

        ServiceType service = new ServiceType();
        service.setWsdl(wsdl);
        service.setTitle("getRandomTitle");
        service.setServiceCode(expectedServiceNameForWSDLQuery);

        wsdl.getService().add(service);

        client.getWsdl().add(wsdl);

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

            private WSDLReader wsdlReader;

            WSDLMessageHandler() throws WSDLException {
                wsdlReader = WSDLFactory.newInstance().newWSDLReader();
            }

            @Override
            public void body(BodyDescriptor bd, InputStream is) throws MimeException, IOException {

                try {
                    definition = wsdlReader.readWSDL(null, new InputSource(is));
                } catch (WSDLException e) {
                    throw new IOException(e);

                }
            }
        }

    }

}
