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

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.TestCertUtil.PKCS12;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.StartStop;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.ByteOrderMark;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509TrustManager;

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
import java.util.Optional;

import static ee.ria.xroad.common.ErrorCodes.translateException;

@Slf4j
class DummyService extends Server implements StartStop {

    private static X509Certificate[] serverCertChain;
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
        ServerConnector connector = new ServerConnector(this);
        connector.setName("httpConnector");
        connector.setPort(ProxyTestSuite.SERVICE_PORT);
        addConnector(connector);

        ServerConnector sslConnector = createSslConnector();
        sslConnector.setName("httpsConnector");
        sslConnector.setPort(ProxyTestSuite.SERVICE_SSL_PORT);
        sslConnector.getConnectionFactories().stream()
                .filter(HttpConnectionFactory.class::isInstance)
                .map(HttpConnectionFactory.class::cast)
                .forEach(httpCf -> {
                    httpCf.getHttpConfiguration().setSendServerVersion(false);
                    Optional.ofNullable(httpCf.getHttpConfiguration().getCustomizer(SecureRequestCustomizer.class))
                            .ifPresent(customizer -> customizer.setSniHostCheck(false));
                });
        addConnector(sslConnector);
    }

    private ServerConnector createSslConnector() throws Exception {
        PKCS12 consumer = TestCertUtil.getConsumer();
        serverCertChain = consumer.certChain;
        serverKey = consumer.key;

        SslContextFactory.Server cf = new SslContextFactory.Server();
        cf.setNeedClientAuth(true);

        cf.setIncludeCipherSuites(SystemProperties.getXroadTLSCipherSuites());
        cf.setSessionCachingEnabled(true);

        SSLContext ctx = SSLContext.getInstance(CryptoUtils.SSL_PROTOCOL);
        ctx.init(new KeyManager[] {new DummyServiceKeyManager()},
                new TrustManager[] {new DummyServiceTrustManager()},
                new SecureRandom());

        cf.setSslContext(ctx);

        return new ServerConnector(this, cf);
    }

    private class ServiceHandler extends AbstractHandler {
        @Override
        public void handle(String target, Request baseRequest,
                HttpServletRequest request, HttpServletResponse response)
                throws IOException, ServletException {
            log.debug("Service simulator received request {}, contentType={}",
                    target, request.getContentType());
            debugRequestHeaders(request);
            try {
                // check if the test case implements custom service response
                AbstractHandler handler = currentTestCase().getServiceHandler();
                if (handler != null) {
                    handler.handle(target, baseRequest, request, response);
                    return;
                }

                currentTestCase().onServiceReceivedHttpRequest(request);

                Message receivedRequest = new Message(
                        request.getInputStream(), request.getContentType()).parse();

                String encoding = request.getCharacterEncoding();

                log.debug("Request: encoding={}, soap={}", encoding,
                        receivedRequest.getSoap());

                currentTestCase().onServiceReceivedRequest(receivedRequest);

                String responseFile = currentTestCase().getResponseFile();

                if (responseFile != null) {
                    try {
                        sendResponseFromFile(responseFile, response);
                    } catch (Exception e) {
                        log.error("An error has occurred when sending response"
                                + " from file '{}': {}", responseFile, e);
                    }
                } else {
                    log.error("Unknown request {}", target);
                }
            } catch (Exception ex) {
                response.sendError(HttpStatus.SC_INTERNAL_SERVER_ERROR, ex.getMessage());
                log.error("Error when reading request", ex);
            } finally {
                baseRequest.setHandled(true);
            }
        }

        private void debugRequestHeaders(HttpServletRequest request) {
            log.debug("Request headers:");

            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                log.debug("\t{} = {}", headerName,
                        request.getHeader(headerName));
            }
        }

        private void sendResponseFromFile(String fileName,
                HttpServletResponse response) throws Exception {
            String responseContentType =
                    currentTestCase().getResponseContentType();

            log.debug("Sending response, content-type = {}, BOM = {}",
                    responseContentType,
                    currentTestCase().addUtf8BomToResponseFile);

            response.setContentType(responseContentType);
            response.setStatus(HttpServletResponse.SC_OK);

            String file = MessageTestCase.QUERIES_DIR + '/' + fileName;

            try (InputStream fileIs = new FileInputStream(file);
                    InputStream responseIs =
                            currentTestCase().changeQueryId(fileIs)) {

                if (currentTestCase().addUtf8BomToResponseFile) {
                    response.getOutputStream().write(
                            ByteOrderMark.UTF_8.getBytes());
                }

                IOUtils.copy(responseIs, response.getOutputStream());
            }

            try (InputStream fileIs = new FileInputStream(file);
                    InputStream responseIs =
                            currentTestCase().changeQueryId(fileIs)) {
                currentTestCase().onSendResponse(
                        new Message(responseIs, currentTestCase()
                                .getResponseServiceContentType()).parse());
            } catch (Exception e) {
                log.error("Error when sending response from file '{}': {}",
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
            log.debug("chooseClientAlias");
            return ALIAS;
        }

        @Override
        public String chooseServerAlias(String keyType, Principal[] issuers,
                Socket socket) {
            log.debug("chooseServerAlias");
            return ALIAS;
        }

        @Override
        public X509Certificate[] getCertificateChain(String alias) {
            return serverCertChain;
        }

        @Override
        public String[] getClientAliases(String keyType, Principal[] issuers) {
            log.debug("getClientAliases");
            return null;
        }

        @Override
        public PrivateKey getPrivateKey(String alias) {
            log.debug("getPrivateKey {} - {}", alias, serverKey);
            return serverKey;
        }

        @Override
        public String[] getServerAliases(String keyType, Principal[] issuers) {
            log.debug("getServerAliases");
            return null;
        }

        @Override
        public String chooseEngineClientAlias(String[] keyType, Principal[] issuers,
                SSLEngine engine) {
            log.debug("chooseEngineClientAlias");
            return ALIAS;
        }

        @Override
        public String chooseEngineServerAlias(String keyType, Principal[] issuers,
                SSLEngine engine) {
            log.debug("chooseEngineServerAlias");
            return ALIAS;
        }
    }

    private static class DummyServiceTrustManager implements X509TrustManager {

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return serverCertChain;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] certs, String authType)
                throws CertificateException {
            log.debug("checkClientTrusted");
        }

        @Override
        public void checkServerTrusted(X509Certificate[] certs, String authType)
                throws CertificateException {
            log.debug("checkServerTrusted");
        }
    }
}
