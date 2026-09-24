/*
 *  The MIT License
 *  Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 *  Copyright (c) 2018 Estonian Information System Authority (RIA),
 *  Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 *  Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package org.niis.xroad.securityserver.restapi.wsdl;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Tests correctness of the WSDL parser.
 */
public class WsdlParserTest {
    private static final String XXE_FIXTURE_DIR = "/wsdl/";
    private static final String BASE_URL_PLACEHOLDER = "BASE_URL";

    private static final List<String> REQUESTED_PATHS = new CopyOnWriteArrayList<>();

    private static WsdlParser wsdlParser;
    private static HttpServer wsdlServer;
    private static String wsdlServerUrl;

    @BeforeClass
    public static void setup() throws Exception {
        wsdlParser = new WsdlParser(mock(ServerConfProvider.class));

        wsdlServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        wsdlServer.createContext("/", WsdlParserTest::serve);
        wsdlServer.start();
        wsdlServerUrl = "http://" + wsdlServer.getAddress().getHostString() + ":" + wsdlServer.getAddress().getPort();
    }

    @AfterClass
    public static void tearDown() {
        wsdlServer.stop(0);
    }

    @Before
    public void resetRequestedPaths() {
        REQUESTED_PATHS.clear();
    }

    /**
     * Test if a valid WSDL is parsed correctly.
     *
     * @throws Exception in case of any errors
     */
    @Test
    public void readValidWsdl() throws Exception {
        Collection<WsdlParser.ServiceInfo> si = wsdlParser.parseWSDL("file:src/test/resources/wsdl/valid.wsdl");
        assertEquals(3, si.size());
    }

    /**
     * Test if an invalid WSDL is recognized.
     *
     * @throws Exception in case of any errors
     */
    @Test(expected = WsdlParser.WsdlParseException.class)
    public void readInvalidWsdl() throws Exception {
        wsdlParser.parseWSDL("file:src/test/resources/wsdl/invalid.wsdl");
    }

    /**
     * Test if an invalid URL is recognized.
     *
     * @throws Exception in case of any errors
     */
    @Test(expected = WsdlParser.WsdlNotFoundException.class)
    public void readWsdlFromInvalidUrl() throws Exception {
        wsdlParser.parseWSDL("http://localhost:1234/foo.wsdl");
    }

    /**
     * Test if a fault XML is recognized.
     *
     * @throws Exception in case of any errors
     */
    @Test(expected = WsdlParser.WsdlParseException.class)
    public void readFaultInsteadOfWsdl() throws Exception {
        wsdlParser.parseWSDL("file:src/test/resources/fault.xml");
    }

    /**
     * Test if NotFound is recognized.
     *
     * @throws Exception in case of any errors
     */
    @Test(expected = WsdlParser.WsdlNotFoundException.class)
    public void tryReadNotFoundWsdl() throws Exception {
        wsdlParser.parseWSDL("file:src/test/resources/wsdl/notfound.wsdl");
    }

    @Test
    public void rejectWsdlWithExternalGeneralEntity() {
        assertRejectedWithoutFurtherRequests("xxe-external-entity.wsdl");
    }

    @Test
    public void rejectWsdlWithExternalDtd() {
        assertRejectedWithoutFurtherRequests("xxe-external-dtd.wsdl");
    }

    @Test
    public void rejectWsdlWithExternalParameterEntity() {
        assertRejectedWithoutFurtherRequests("xxe-parameter-entity.wsdl");
    }

    private static void assertRejectedWithoutFurtherRequests(String fixture) {
        String wsdlPath = XXE_FIXTURE_DIR + fixture;

        Throwable thrown = catchThrowable(() -> wsdlParser.parseWSDL(wsdlServerUrl + wsdlPath));

        assertThat(REQUESTED_PATHS).containsExactly(wsdlPath);
        assertThat(thrown)
                .isInstanceOf(WsdlParser.WsdlParseException.class)
                .hasStackTraceContaining("disallow-doctype-decl");
    }

    private static void serve(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        REQUESTED_PATHS.add(path);

        byte[] body = new byte[0];
        try (InputStream fixture = WsdlParserTest.class.getResourceAsStream(path)) {
            if (fixture != null) {
                body = new String(fixture.readAllBytes(), StandardCharsets.UTF_8)
                        .replace(BASE_URL_PLACEHOLDER, wsdlServerUrl)
                        .getBytes(StandardCharsets.UTF_8);
            }
        }

        exchange.sendResponseHeaders(200, body.length == 0 ? -1 : body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
