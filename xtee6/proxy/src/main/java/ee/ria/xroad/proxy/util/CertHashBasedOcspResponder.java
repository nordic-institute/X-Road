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
package ee.ria.xroad.proxy.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.bouncycastle.cert.ocsp.OCSPResp;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.util.MultiPartOutputStream;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.common.util.MimeUtils;
import ee.ria.xroad.common.util.StartStop;
import ee.ria.xroad.proxy.conf.KeyConf;

/**
 * Service responsible for responding with OCSP responses of
 * SSL certificates identified with the certificate hashes.
 *
 * Accepts only GET requests. Certificate hashes are specified with
 * url parameter "cert".
 *
 * To retrieve OCSP responses, send a GET request to this service:
 * http://<host>:<port>/?cert=hash1&cert=hash2&cert=hash3 ...
 */
public class CertHashBasedOcspResponder implements StartStop {

    private static final int THREAD_POOL_SIZE = 5;

    private static final Logger LOG =
            LoggerFactory.getLogger(CertHashBasedOcspResponder.class);

    private static final String METHOD_HEAD = "HEAD";
    private static final String METHOD_GET = "GET";

    private static final String CERT_PARAM = "cert";

    private final Server server = new Server();

    /**
     * Constructs a cert hash responder.
     */
    public CertHashBasedOcspResponder() {
        this(SystemProperties.getOcspResponderListenAddress());
    }

    /**
     * Constructs a cert hash responder that listens on the specified address.
     * @param host the address this responder should listen at
     */
    public CertHashBasedOcspResponder(String host) {
        SelectChannelConnector ocspConnector = new SelectChannelConnector();
        ocspConnector.setName("OcspResponseConnector");
        ocspConnector.setPort(SystemProperties.getOcspResponderPort());
        ocspConnector.setHost(host);

        server.addConnector(ocspConnector);
        server.setHandler(new RequestHandler());
        server.setThreadPool(new QueuedThreadPool(THREAD_POOL_SIZE));
        server.setSendServerVersion(false);
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

    private void doHandleRequest(HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        String[] hashes = getCertHashes(request);
        List<OCSPResp> ocspResponses = getOcspResponses(hashes);

        LOG.debug("Returning OCSP responses for cert hashes: "
                + Arrays.toString(hashes));

        MultiPartOutputStream mpResponse =
                new MultiPartOutputStream(response.getOutputStream());

        response.setContentType(
                MimeUtils.mpRelatedContentType(mpResponse.getBoundary()));
        response.setStatus(HttpServletResponse.SC_OK);

        for (OCSPResp ocsp : ocspResponses) {
            mpResponse.startPart(MimeTypes.OCSP_RESPONSE);
            mpResponse.write(ocsp.getEncoded());
        }

        mpResponse.close();
    }

    private class RequestHandler extends AbstractHandler {
        @Override
        public void handle(String target, Request baseRequest,
                HttpServletRequest request, HttpServletResponse response)
                throws IOException, ServletException {
            LOG.trace("Received {} request from {}", baseRequest.getMethod(),
                    request.getRemoteAddr());
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
                        throw new Exception("Invalid request method: "
                                + request.getMethod());
                }
            } catch (Exception e) {
                LOG.error("Error getting OCSP responses", e);
                response.sendError(
                        HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        e.getMessage());
            } finally {
                baseRequest.setHandled(true);
            }
        }
    }

    private static List<OCSPResp> getOcspResponses(String[] hashes)
            throws Exception {
        List<OCSPResp> ocspResponses = new ArrayList<>(hashes.length);
        for (String certHash : hashes) {
            ocspResponses.add(getOcspResponse(certHash));
        }

        return ocspResponses;
    }

    private static OCSPResp getOcspResponse(String certHash) throws Exception {
        OCSPResp ocsp = KeyConf.getOcspResponse(certHash);
        if (ocsp == null) {
            throw new Exception("Could not find OCSP response for certificate "
                    + certHash);
        }

        return ocsp;
    }

    private static String[] getCertHashes(HttpServletRequest request)
            throws Exception {
        String[] paramValues = request.getParameterValues(CERT_PARAM);
        if (paramValues.length < 1) {
            throw new Exception("Could not get cert hashes");
        }

        return paramValues;
    }
}
