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
package ee.ria.xroad.proxy.testsuite;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509TrustManager;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.TestCertUtil.PKCS12;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.StartStop;

import static ee.ria.xroad.common.ErrorCodes.translateException;

@SuppressWarnings("unchecked")
class DummyService extends Server implements StartStop {

    private static final Logger LOG =
            LoggerFactory.getLogger(DummyService.class);

    private static X509Certificate serverCert;
    private static PrivateKey serverKey;

    DummyService() {
        super();
        try {
            setupConnectors();
            setHandler(new ServiceHandler());
        } catch (Exception e) {
            throw translateException(e);
        }
    }

    private void setupConnectors() throws Exception {
        SelectChannelConnector connector = new SelectChannelConnector();
        connector.setName("httpConnector");
        connector.setPort(ProxyTestSuite.SERVICE_PORT);
        connector.setSoLingerTime(0);
        connector.setMaxIdleTime(0);
        addConnector(connector);

        SelectChannelConnector sslConnector = createSslConnector();
        sslConnector.setName("httpsConnector");
        sslConnector.setPort(ProxyTestSuite.SERVICE_SSL_PORT);
        sslConnector.setSoLingerTime(0);
        sslConnector.setMaxIdleTime(0);
        sslConnector.setAcceptors(2 * Runtime.getRuntime().availableProcessors());
        addConnector(sslConnector);
    }

    private SelectChannelConnector createSslConnector() throws Exception {
        PKCS12 consumer = TestCertUtil.getConsumer();
        serverCert = consumer.cert;
        serverKey = consumer.key;

        SslContextFactory cf = new SslContextFactory(false);
        cf.setNeedClientAuth(true);

        cf.setIncludeCipherSuites(CryptoUtils.getINCLUDED_CIPHER_SUITES());
        cf.setSessionCachingEnabled(true);

        SSLContext ctx = SSLContext.getInstance(CryptoUtils.SSL_PROTOCOL);
        ctx.init(new KeyManager[] {new DummyServiceKeyManager()},
                new TrustManager[] {new DummyServiceTrustManager()},
                new SecureRandom());

        cf.setSslContext(ctx);

        return new SslSelectChannelConnector(cf);
    }

    private class ServiceHandler extends AbstractHandler {
        @Override
        public void handle(String target, Request baseRequest,
                HttpServletRequest request, HttpServletResponse response)
                throws IOException, ServletException {
            LOG.debug("Service simulator received request {}, contentType={}",
                    target, request.getContentType());
            debugRequestHeaders(request);
            try {
                // check if the test case implements custom service response
                AbstractHandler handler = currentTestCase().getServiceHandler();
                if (handler != null) {
                    handler.handle(target, baseRequest, request, response);
                    return;
                }

                Message receivedRequest = new Message(
                        request.getInputStream(), request.getContentType());

                String encoding = request.getCharacterEncoding();
                LOG.debug("Request: encoding={}, soap={}", encoding,
                        receivedRequest.getSoap());

                currentTestCase().onReceiveRequest(receivedRequest);

                String responseFile = currentTestCase().getResponseFile();
                if (responseFile != null) {
                    try {
                        sendResponseFromFile(responseFile, response);
                    } catch (Exception e) {
                        LOG.error("An error has occurred when sending response"
                                + " from file '{}': {}", responseFile, e);
                    }
                } else {
                    LOG.error("Unknown request {}", target);
                }
            } catch (Exception ex) {
                LOG.error("Error when reading request", ex);
            } finally {
                baseRequest.setHandled(true);
            }
        }

        private void debugRequestHeaders(HttpServletRequest request) {
            LOG.debug("Request headers:");

            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                LOG.debug("\t{} = {}", headerName,
                        request.getHeader(headerName));
            }
        }

        private void sendResponseFromFile(String fileName,
                HttpServletResponse response) throws Exception {
            String responseContentType =
                    currentTestCase().getResponseContentType();

            LOG.debug("Sending response, content-type = {}",
                    responseContentType);

            response.setContentType(responseContentType);
            response.setStatus(HttpServletResponse.SC_OK);

            String file = MessageTestCase.QUERIES_DIR + '/' + fileName;

            try (InputStream fileIs = new FileInputStream(file);
                    InputStream responseIs =
                            currentTestCase().changeQueryId(fileIs)) {

                IOUtils.copy(responseIs, response.getOutputStream());
            }

            try (InputStream fileIs = new FileInputStream(file);
                    InputStream responseIs =
                            currentTestCase().changeQueryId(fileIs)) {
                currentTestCase().onSendResponse(
                        new Message(responseIs, currentTestCase()
                                .getResponseServiceContentType()));
            } catch (Exception e) {
                LOG.error("Error when sending response from file '{}': {}",
                        file, e.toString());
            }
        }
    }

    private static MessageTestCase currentTestCase() {
        return ProxyTestSuite.currentTestCase;
    }

    private static class DummyServiceKeyManager extends X509ExtendedKeyManager {

        private static final String ALIAS = "AuthKeyManager";

        @Override
        public String chooseClientAlias(String[] keyType, Principal[] issuers,
                Socket socket) {
            LOG.debug("chooseClientAlias");
            return ALIAS;
        }

        @Override
        public String chooseServerAlias(String keyType, Principal[] issuers,
                Socket socket) {
            LOG.debug("chooseServerAlias");
            return ALIAS;
        }

        @Override
        public X509Certificate[] getCertificateChain(String alias) {
            return new X509Certificate[] {serverCert};
        }

        @Override
        public String[] getClientAliases(String keyType, Principal[] issuers) {
            LOG.debug("getClientAliases");
            return null;
        }

        @Override
        public PrivateKey getPrivateKey(String alias) {
            LOG.debug("getPrivateKey {} - {}", alias, serverKey);
            return serverKey;
        }

        @Override
        public String[] getServerAliases(String keyType, Principal[] issuers) {
            LOG.debug("getServerAliases");
            return null;
        }

        @Override
        public String chooseEngineClientAlias(String[] keyType, Principal[] issuers,
                SSLEngine engine) {
            LOG.debug("chooseEngineClientAlias");
            return ALIAS;
        }

        @Override
        public String chooseEngineServerAlias(String keyType, Principal[] issuers,
                SSLEngine engine) {
            LOG.debug("chooseEngineServerAlias");
            return ALIAS;
        }
    }

    private static class DummyServiceTrustManager implements X509TrustManager {

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            LOG.debug("getAcceptedIssuers {}", serverCert);
            return new X509Certificate[] {serverCert};
        }

        @Override
        public void checkClientTrusted(X509Certificate[] certs, String authType)
                throws CertificateException {
            LOG.debug("checkClientTrusted");
        }

        @Override
        public void checkServerTrusted(X509Certificate[] certs, String authType)
                throws CertificateException {
            LOG.debug("checkServerTrusted");
        }
    }
}
