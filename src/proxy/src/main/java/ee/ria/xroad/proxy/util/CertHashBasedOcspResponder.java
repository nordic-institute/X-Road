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
package ee.ria.xroad.proxy.util;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.common.util.MimeUtils;
import ee.ria.xroad.common.util.StartStop;
import ee.ria.xroad.proxy.conf.KeyConf;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.MultiPartOutputStream;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.xml.XmlConfiguration;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Service responsible for responding with OCSP responses of SSL certificates identified with the certificate hashes.
 *
 * Accepts only GET requests. Certificate hashes are specified with url parameter "cert".
 *
 * To retrieve OCSP responses, send a GET request to this service:
 * http://<host>:<port>/?cert=hash1&cert=hash2&cert=hash3 ...
 */
@Slf4j
public class CertHashBasedOcspResponder implements StartStop {

    private static final String METHOD_HEAD = "HEAD";
    private static final String METHOD_GET = "GET";

    private static final String CERT_PARAM = "cert";

    private final Server server = new Server();

    /**
     * Constructs a cert hash responder.
     * @throws Exception in case of any errors
     */
    public CertHashBasedOcspResponder() throws Exception {
        this(SystemProperties.getOcspResponderListenAddress());
    }

    /**
     * Constructs a cert hash responder that listens on the specified address.
     * @param host the address this responder should listen at
     * @throws Exception in case of any errors
     */
    public CertHashBasedOcspResponder(String host) throws Exception {
        configureServer();
        createConnector(host);
        createHandler();
    }

    private void configureServer() throws Exception {
        log.trace("configureServer()");

        Path file = Paths.get(SystemProperties.getJettyOcspResponderConfFile());

        log.debug("Configuring server from {}", file);
        try (Resource in = Resource.newResource(file)) {
            new XmlConfiguration(in).configure(server);
        }
    }

    private void createConnector(String host) {
        log.trace("createConnector({})", host);

        ServerConnector ocspConnector = new ServerConnector(server);

        ocspConnector.setName("OcspResponseConnector");
        ocspConnector.setPort(SystemProperties.getOcspResponderPort());
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

    @Override
    public void start() throws Exception {
        server.start();
    }

    @Override
    public void stop() throws Exception {
        server.stop();
    }

    @Override
    public void join() throws InterruptedException {
        if (server.getThreadPool() != null) {
            server.join();
        }
    }

    private void doHandleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String[] hashes = getCertSha1Hashes(request);
        List<OCSPResp> ocspResponses = getOcspResponses(hashes);

        log.debug("Returning OCSP responses for cert hashes: " + Arrays.toString(hashes));

        MultiPartOutputStream mpResponse = new MultiPartOutputStream(response.getOutputStream());

        response.setContentType(MimeUtils.mpRelatedContentType(mpResponse.getBoundary(), MimeTypes.OCSP_RESPONSE));
        response.setStatus(HttpServletResponse.SC_OK);

        for (OCSPResp ocsp : ocspResponses) {
            mpResponse.startPart(MimeTypes.OCSP_RESPONSE);
            mpResponse.write(ocsp.getEncoded());
        }

        mpResponse.close();
    }

    private class RequestHandler extends AbstractHandler {
        @Override
        public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
                throws IOException, ServletException {
            log.trace("Received {} request from {}", baseRequest.getMethod(), request.getRemoteAddr());

            try {
                switch (baseRequest.getMethod()) {
                    case METHOD_HEAD:
                        // heart beat - simply return OK
                        response.setStatus(HttpServletResponse.SC_OK);

                        break;
                    case METHOD_GET:
                        doHandleRequest(request, response);

                        break;
                    default:
                        throw new Exception("Invalid request method: " + request.getMethod());
                }
            } catch (Exception e) {
                log.error("Error getting OCSP responses", e);

                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
            } finally {
                baseRequest.setHandled(true);
            }
        }
    }

    private static List<OCSPResp> getOcspResponses(String[] certHashes) throws Exception {
        List<OCSPResp> ocspResponses = new ArrayList<>(certHashes.length);

        for (String certHash : certHashes) {
            ocspResponses.add(getOcspResponse(certHash));
        }

        return ocspResponses;
    }

    private static OCSPResp getOcspResponse(String certHash) throws Exception {
        OCSPResp ocsp = KeyConf.getOcspResponse(certHash);

        if (ocsp == null) {
            throw new Exception("Could not find OCSP response for certificate " + certHash);
        }

        return ocsp;
    }

    private static String[] getCertSha1Hashes(HttpServletRequest request) throws Exception {
        // TODO sha256 cert hashes should be read from "cert_hash" param instead once 7.3.x is no longer supported
        String[] paramValues = request.getParameterValues(CERT_PARAM);

        if (paramValues.length < 1) {
            throw new Exception("Could not get cert hashes");
        }

        return paramValues;
    }
}
