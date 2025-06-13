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
package org.niis.xroad.proxy.core.util;

import ee.ria.xroad.common.util.JettyUtils;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.common.util.MimeUtils;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.MultiPartOutputStream;
import org.eclipse.jetty.xml.XmlConfiguration;
import org.niis.xroad.keyconf.KeyConfProvider;
import org.niis.xroad.proxy.core.ProxyProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.eclipse.jetty.http.HttpStatus.INTERNAL_SERVER_ERROR_500;
import static org.eclipse.jetty.http.HttpStatus.OK_200;
import static org.eclipse.jetty.server.Request.getRemoteAddr;
import static org.niis.xroad.proxy.core.util.CertHashBasedOcspResponderClient.SHA_256_CERT_PARAM;

/**
 * Service responsible for responding with OCSP responses of SSL certificates identified with the certificate hashes.
 * <p>
 * Accepts only GET requests. Certificate hashes are specified with url parameter "cert".
 * <p>
 * To retrieve OCSP responses, send a GET request to this service:
 * http://<host>:<port>/?cert=hash1&cert=hash2&cert=hash3 ...
 */
@Slf4j
@ApplicationScoped
@Startup
public class CertHashBasedOcspResponder {

    private static final String METHOD_HEAD = "HEAD";
    private static final String METHOD_GET = "GET";

    private final ProxyProperties.OcspResponderProperties ocspResponderProperties;
    private final KeyConfProvider keyConfProvider;
    private final Server server = new Server();

    /**
     * Constructs a cert hash responder that listens on the specified address.
     */
    public CertHashBasedOcspResponder(ProxyProperties.OcspResponderProperties ocspResponderProperties, KeyConfProvider keyConfProvider) {
        this.keyConfProvider = keyConfProvider;
        this.ocspResponderProperties = ocspResponderProperties;
    }

    private void configureServer() throws Exception {
        log.trace("configureServer()");

        var file = ocspResponderProperties.jettyConfigurationFile();

        log.debug("Configuring server from {}", file);
        new XmlConfiguration(JettyUtils.toResource(file)).configure(server);
    }

    private void createConnector() {
        String host = ocspResponderProperties.listenAddress();
        log.trace("createConnector({})", host);

        ServerConnector ocspConnector = new ServerConnector(server);

        ocspConnector.setName("OcspResponseConnector");
        ocspConnector.setPort(ocspResponderProperties.port());
        ocspConnector.setHost(host);
        ocspConnector.getConnectionFactories().stream()
                .filter(HttpConnectionFactory.class::isInstance)
                .map(HttpConnectionFactory.class::cast)
                .forEach(httpCf -> {
                    httpCf.getHttpConfiguration().setSendServerVersion(false);
                    Optional.ofNullable(httpCf.getHttpConfiguration().getCustomizer(SecureRequestCustomizer.class))
                            .ifPresent(customizer -> {
                                customizer.setSniHostCheck(false);
                            });
                });

        server.addConnector(ocspConnector);
    }

    private void createHandler() {
        log.trace("createHandler()");

        server.setHandler(new RequestHandler());
    }

    @PostConstruct
    public void afterPropertiesSet() throws Exception {
        configureServer();
        createConnector();
        createHandler();

        server.start();
    }

    @PreDestroy
    public void destroy() throws Exception {
        server.stop();
    }

    private void doHandleRequest(Request request, Response response) throws Exception {
        var hashes = getCertSha256Hashes(request);
        List<OCSPResp> ocspResponses = getOcspResponses(hashes);

        log.debug("Returning OCSP responses for cert hashes: " + hashes);

        try (MultiPartOutputStream mpResponse = new MultiPartOutputStream(Content.Sink.asOutputStream(response))) {

            JettyUtils.setContentType(response, MimeUtils.mpRelatedContentType(mpResponse.getBoundary(), MimeTypes.OCSP_RESPONSE));
            response.setStatus(OK_200);

            for (OCSPResp ocsp : ocspResponses) {
                mpResponse.startPart(MimeTypes.OCSP_RESPONSE);
                mpResponse.write(ocsp.getEncoded());
            }
        }
    }

    private final class RequestHandler extends Handler.Abstract {
        @Override
        public boolean handle(Request request, Response response, Callback callback) {
            log.trace("Received {} request from {}", request.getMethod(), getRemoteAddr(request));

            try {
                switch (request.getMethod()) {
                    case METHOD_HEAD:
                        // heart beat - simply return OK
                        response.setStatus(OK_200);

                        break;
                    case METHOD_GET:
                        doHandleRequest(request, response);

                        break;
                    default:
                        throw new Exception("Invalid request method: " + request.getMethod());
                }
            } catch (Exception e) {
                log.error("Error getting OCSP responses", e);

                response.setStatus(INTERNAL_SERVER_ERROR_500);
                Content.Sink.write(response, true, e.getMessage(), callback);
            } finally {
                callback.succeeded();
            }
            return true;
        }
    }

    private List<OCSPResp> getOcspResponses(List<String> certHashes) throws Exception {
        List<OCSPResp> ocspResponses = new ArrayList<>(certHashes.size());

        for (String certHash : certHashes) {
            ocspResponses.add(getOcspResponse(certHash));
        }

        return ocspResponses;
    }

    private OCSPResp getOcspResponse(String certHash) throws Exception {
        OCSPResp ocsp = keyConfProvider.getOcspResponse(certHash);

        if (ocsp == null) {
            throw new Exception("Could not find OCSP response for certificate " + certHash);
        }

        return ocsp;
    }

    private static List<String> getCertSha256Hashes(Request request) throws Exception {
        var paramValues = Request.getParameters(request).getValues(SHA_256_CERT_PARAM);

        if (paramValues == null || paramValues.isEmpty()) {
            throw new Exception("Could not get cert hashes");
        }

        return paramValues;
    }
}
