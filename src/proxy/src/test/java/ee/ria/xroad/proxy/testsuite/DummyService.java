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

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.ByteOrderMark;
import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509TrustManager;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Optional;

import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.util.JettyUtils.getCharacterEncoding;
import static ee.ria.xroad.common.util.JettyUtils.getContentType;
import static ee.ria.xroad.common.util.JettyUtils.getTarget;
import static ee.ria.xroad.common.util.JettyUtils.setContentType;
import static org.eclipse.jetty.http.HttpStatus.OK_200;
import static org.eclipse.jetty.io.Content.Sink.asOutputStream;
import static org.eclipse.jetty.io.Content.Source.asInputStream;

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
        ctx.init(new KeyManager[]{new DummyServiceKeyManager()},
                new TrustManager[]{new DummyServiceTrustManager()},
                new SecureRandom());

        cf.setSslContext(ctx);

        return new ServerConnector(this, cf);
    }

    private static final class ServiceHandler extends Handler.Abstract {
        @Override
        public boolean handle(Request request, Response response, Callback callback) {
            var target = getTarget(request);
            log.debug("Service simulator received request {}, contentType={}",
                    target, getContentType(request));
            debugRequestHeaders(request);
            try {
                // check if the test case implements custom service response
                Handler.Abstract handler = currentTestCase().getServiceHandler();
                if (handler != null) {
                    return handler.handle(request, response, callback);
                }

                currentTestCase().onServiceReceivedHttpRequest(request);

                Message receivedRequest = new Message(
                        asInputStream(request), getContentType(request)).parse();

                String encoding = getCharacterEncoding(request);

                log.debug("Request: encoding={}, soap={}", encoding,
                        receivedRequest.getSoap());

                currentTestCase().onServiceReceivedRequest(receivedRequest);

                String responseFile = currentTestCase().getResponseFile();

                if (responseFile != null) {
                    try {
                        sendResponseFromFile(responseFile, response);
                    } catch (Exception e) {
                        log.error("An error has occurred when sending response from file '{}'", responseFile, e);
                    }
                } else {
                    log.error("Unknown request {}", target);
                }
                callback.succeeded();
            } catch (Exception ex) {
                Response.writeError(request, response, callback, HttpStatus.INTERNAL_SERVER_ERROR_500, ex.getMessage());
                log.error("Error when reading request", ex);
            }
            return true;
        }

        private void debugRequestHeaders(Request request) {
            log.debug("Request headers:");

            var headerNames = request.getHeaders().getFieldNamesCollection();
            for (String headerName : headerNames) {
                log.debug("\t{} = {}", headerName,
                        request.getHeaders().get(headerName));
            }
        }

        private void sendResponseFromFile(String fileName,
                                          Response response) throws Exception {
            String responseContentType =
                    currentTestCase().getResponseContentType();

            log.debug("Sending response, content-type = {}, BOM = {}",
                    responseContentType,
                    currentTestCase().addUtf8BomToResponseFile);

            setContentType(response, responseContentType);
            response.setStatus(OK_200);

            String file = MessageTestCase.QUERIES_DIR + '/' + fileName;

            try (
                    InputStream fileIs = new FileInputStream(file);
                    InputStream responseIs = currentTestCase().changeQueryId(fileIs);
                    var responseOs = asOutputStream(response)
            ) {

                if (currentTestCase().addUtf8BomToResponseFile) {
                    responseOs.write(
                            ByteOrderMark.UTF_8.getBytes());
                }

                IOUtils.copy(responseIs, responseOs);
            }

            try (
                    InputStream fileIs = new FileInputStream(file);
                    InputStream responseIs = currentTestCase().changeQueryId(fileIs)
            ) {
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

    private static final class DummyServiceKeyManager extends X509ExtendedKeyManager {

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

    private static final class DummyServiceTrustManager implements X509TrustManager {

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
