package ee.cyber.sdsb.proxy.util;

import java.io.IOException;
import java.security.cert.X509Certificate;
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

import ee.cyber.sdsb.common.PortNumbers;
import ee.cyber.sdsb.common.util.CryptoUtils;
import ee.cyber.sdsb.common.util.MimeTypes;
import ee.cyber.sdsb.common.util.MimeUtils;
import ee.cyber.sdsb.common.util.StartStop;
import ee.cyber.sdsb.proxy.conf.ServerConf;

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

    private static final Logger LOG =
            LoggerFactory.getLogger(CertHashBasedOcspResponder.class);

    private static final String METHOD = "GET";
    private static final String CERT_PARAM = "cert";

    private Server server = new Server();

    public CertHashBasedOcspResponder() {
        this(ServerConf.getConnectorHost());
    }

    public CertHashBasedOcspResponder(String host) {
        SelectChannelConnector ocspConnector = new SelectChannelConnector();
        ocspConnector.setName("OcspResponseConnector");
        ocspConnector.setPort(PortNumbers.PROXY_OCSP_PORT);
        ocspConnector.setHost(host);

        server.addConnector(ocspConnector);
        server.setHandler(new RequestHandler());
        server.setThreadPool(new QueuedThreadPool(5));
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

    private class RequestHandler extends AbstractHandler {
        @Override
        public void handle(String target, Request baseRequest,
                HttpServletRequest request, HttpServletResponse response)
                throws IOException, ServletException {
            try {
                checkRequestMethod(request);

                String[] hashes = getCertHashes(request);
                List<OCSPResp> ocspResponses = getOcspResponses(hashes);

                LOG.debug("Returning OCSP responses for cert hashes: "
                        + Arrays.toString(hashes));

                MultiPartOutputStream mpResponse =
                        new MultiPartOutputStream(response.getOutputStream());

                response.setContentType(MimeUtils.mpRelatedContentType(
                                mpResponse.getBoundary()));
                response.setStatus(HttpServletResponse.SC_OK);

                for (OCSPResp ocsp : ocspResponses) {
                    mpResponse.startPart(MimeTypes.OCSP_RESPONSE);
                    mpResponse.write(ocsp.getEncoded());
                }

                mpResponse.close();
            } catch (Exception e) {
                LOG.error("Error getting OCSP response", e);
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
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
        X509Certificate cert = getCert(certHash);
        OCSPResp ocsp = ServerConf.getOcspResponse(cert);
        if (ocsp == null) {
            throw new Exception("Could not find OCSP response");
        }

        return ocsp;
    }

    private static X509Certificate getCert(String certHash) throws Exception {
        List<X509Certificate> certs = ServerConf.getCertsForOcsp();
        for (X509Certificate cert : certs) {
            String hash = CryptoUtils.calculateCertHexHash(cert);
            if (certHash.equals(hash)) {
                return cert;
            }
        }

        return null;
    }

    private static String[] getCertHashes(HttpServletRequest request)
        throws Exception {
        String[] paramValues = request.getParameterValues(CERT_PARAM);
        if (paramValues.length < 1) {
            throw new Exception("Could not get cert hashes");
        }

        return paramValues;
    }

    private static void checkRequestMethod(HttpServletRequest request)
            throws Exception {
        if (!request.getMethod().equalsIgnoreCase(METHOD)) {
            throw new Exception(
                    "Invalid request method: " + request.getMethod());
        }
    }
}
