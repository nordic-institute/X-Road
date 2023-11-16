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
package ee.ria.xroad.proxy.testsuite;

import ee.ria.xroad.common.PortNumbers;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.util.StartStop;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import static ee.ria.xroad.common.util.CryptoUtils.DEFAULT_DIGEST_ALGORITHM_ID;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_HASH_ALGO_ID;

@Slf4j
class DummyServerProxy extends Server implements StartStop {

    DummyServerProxy() {
        ServerConnector connector = new ServerConnector(this);

        connector.setName("ClientConnector");
        connector.setHost("127.0.0.2");

        final var port = System.getProperty(SystemProperties.PROXY_SERVER_PORT, String.valueOf(PortNumbers.PROXY_PORT));
        connector.setPort(Integer.parseInt(port));

        addConnector(connector);
        setHandler(new ServiceHandler());
    }

    private class ServiceHandler extends AbstractHandler {
        @Override
        public void handle(String target, Request baseRequest,
                HttpServletRequest request, HttpServletResponse response)
                throws IOException, ServletException {
            log.debug("Proxy simulator received request {}, contentType={}",
                    target, request.getContentType());

            response.addHeader("Connection", "close");
            response.addHeader(HEADER_HASH_ALGO_ID, DEFAULT_DIGEST_ALGORITHM_ID);

            // check if the test case implements custom service response
            AbstractHandler handler = currentTestCase().getServerProxyHandler();
            if (handler != null) {
                handler.handle(target, baseRequest, request, response);
                return;
            }

            // Read all of the request (and copy it to /dev/null).
            IOUtils.copy(request.getInputStream(), new NullOutputStream());

            if (currentTestCase().getResponseFile() != null) {
                createResponseFromFile(currentTestCase().getResponseFile(),
                        baseRequest, response);
            } else {
                log.error("Unknown request {}", target);
            }
        }

        private void createResponseFromFile(String fileName, Request baseRequest,
                HttpServletResponse response) {
            String file = MessageTestCase.QUERIES_DIR + '/' + fileName;
            try {
                response.setContentType(
                        currentTestCase().getResponseContentType());
                response.setStatus(HttpServletResponse.SC_OK);
                try (InputStream fileIs = new FileInputStream(file);
                        InputStream responseIs =
                                currentTestCase().changeQueryId(fileIs)) {
                    IOUtils.copy(responseIs, response.getOutputStream());
                }
            } catch (FileNotFoundException e) {
                log.error("Could not find answer file: " + file, e);
                return;
            } catch (Exception e) {
                log.error("An error has occured when sending response "
                        + "from file " + file, e);
            }

            baseRequest.setHandled(true);
        }
    }

    private static MessageTestCase currentTestCase() {
        return ProxyTestSuite.currentTestCase;
    }
}
