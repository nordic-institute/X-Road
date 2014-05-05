package ee.cyber.sdsb.proxy.util;

import java.io.IOException;
import java.net.ConnectException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPException;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import ee.cyber.sdsb.common.OcspTestUtils;
import ee.cyber.sdsb.common.TestCertUtil;
import ee.cyber.sdsb.common.conf.GlobalConf;
import ee.cyber.sdsb.common.ocsp.OcspVerifier;
import ee.cyber.sdsb.proxy.EmptyGlobalConf;
import ee.cyber.sdsb.proxy.conf.KeyConf;
import ee.cyber.sdsb.proxy.conf.ServerConf;
import ee.cyber.sdsb.proxy.testsuite.EmptyKeyConf;
import ee.cyber.sdsb.proxy.testsuite.EmptyServerConf;

import static ee.cyber.sdsb.common.util.CryptoUtils.calculateCertHexHash;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class OcspClientTest {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private static final int RESPONDER_PORT = 8080;

    private static final String RESPONDER_URI =
            "http://127.0.0.1:" + RESPONDER_PORT;

    private Server ocspResponder;

    private X509Certificate ocspResponderCert;

    // --- test cases

    @Test
    public void goodCertificateStatus() throws Exception {
        X509Certificate subject = getDefaultClientCert();

        GlobalConf.reload(new DefaultTestGlobalConf());

        Date thisUpdate = new DateTime().plusDays(1).toDate();

        OCSPResp response = OcspTestUtils.createOCSPResponse(subject,
                GlobalConf.getCaCert(subject), ocspResponderCert,
                KeyConf.getOcspRequestKey(null),
                CertificateStatus.GOOD, thisUpdate, null);

        ocspResponder.setHandler(
                new TestOCSPResponder(response.getEncoded()));
        ocspResponder.start();

        X509Certificate issuer = GlobalConf.getCaCert(subject);
        PrivateKey signerKey = KeyConf.getOcspRequestKey(subject);

        // our test responder doesn't really care for the sender certificate,
        // so we won't use it in these tests
        X509Certificate signerCert = null;

        OcspClient.queryAndUpdateCertStatus(
                subject, issuer, signerKey, signerCert);

        OCSPResp ocsp = ServerConf.getOcspResponse(subject);
        assertNotNull(ocsp);

        OcspVerifier.verify(ocsp, subject, GlobalConf.getCaCert(subject));
    }

    @Test
    public void goodCertificateStatusFromSecondResponder() throws Exception {
        X509Certificate subject = getDefaultClientCert();

        GlobalConf.reload(new DefaultTestGlobalConf() {
            @Override
            public List<String> getOcspResponderAddresses(X509Certificate org) {
                return Arrays.asList("http://127.0.0.1:1234", RESPONDER_URI);
            }
        });

        Date thisUpdate = new DateTime().plusDays(1).toDate();

        OCSPResp response = OcspTestUtils.createOCSPResponse(subject,
                GlobalConf.getCaCert(subject), ocspResponderCert,
                KeyConf.getOcspRequestKey(null),
                CertificateStatus.GOOD, thisUpdate, null);

        ocspResponder.setHandler(
                new TestOCSPResponder(response.getEncoded()));
        ocspResponder.start();

        X509Certificate issuer = GlobalConf.getCaCert(subject);
        PrivateKey signerKey = KeyConf.getOcspRequestKey(subject);
        X509Certificate signerCert = null;

        OcspClient.queryAndUpdateCertStatus(
                subject, issuer, signerKey, signerCert);

        OCSPResp ocsp = ServerConf.getOcspResponse(subject);
        assertNotNull(ocsp);

        OcspVerifier.verify(ocsp, subject, GlobalConf.getCaCert(subject));
    }

    @Test
    public void noResponseFromOCSPServer() throws Exception {
        X509Certificate subject = getDefaultClientCert();

        GlobalConf.reload(new DefaultTestGlobalConf());

        ocspResponder.setHandler(new TestOCSPResponder(null));
        ocspResponder.start();

        X509Certificate issuer = GlobalConf.getCaCert(subject);
        X509Certificate signerCert = null;
        PrivateKey signerKey = KeyConf.getOcspRequestKey(subject);

        thrown.expect(IOException.class);
        OcspClient.fetchResponse(
                RESPONDER_URI, subject, issuer, signerKey, signerCert);
    }

    @Test
    public void faultyResponseFromOCSPServer() throws Exception {
        X509Certificate subject = getDefaultClientCert();

        GlobalConf.reload(new DefaultTestGlobalConf());

        ocspResponder.setHandler(new TestOCSPResponder("abcdefgh".getBytes()));
        ocspResponder.start();

        X509Certificate issuer = GlobalConf.getCaCert(subject);
        X509Certificate signerCert = null;
        PrivateKey signerKey = KeyConf.getOcspRequestKey(subject);

        thrown.expect(OCSPException.class);
        OcspClient.fetchResponse(
                RESPONDER_URI, subject, issuer, signerKey, signerCert);
    }

    @Test
    public void cannotConnectNoResponders() throws Exception {
        // this certificate does not contain responder URI in AIA extension.
        X509Certificate subject = TestCertUtil.getCertChainCert("user_0.p12");

        GlobalConf.reload(new DefaultTestGlobalConf() {
            @Override
            public List<String> getOcspResponderAddresses(X509Certificate org) {
                return new ArrayList<>();
            }
        });

        ocspResponder.start();

        X509Certificate issuer = GlobalConf.getCaCert(subject);
        X509Certificate signerCert = null;
        PrivateKey signerKey = KeyConf.getOcspRequestKey(subject);

        thrown.expect(ConnectException.class);
        try {
            OcspClient.queryAndUpdateCertStatus(
                    subject, issuer, signerKey, signerCert);
            fail("Should fail to query certificate status");
        } finally {
            assertFalse(ServerConf.isCachedOcspResponse(hash(subject)));
        }
    }

    @Test
    public void cannotConnect() throws Exception {
        X509Certificate subject = getDefaultClientCert();

        GlobalConf.reload(new DefaultTestGlobalConf());

        X509Certificate issuer = GlobalConf.getCaCert(subject);
        X509Certificate signerCert = null;
        PrivateKey signerKey = KeyConf.getOcspRequestKey(subject);

        thrown.expect(IOException.class);
        try {
            OcspClient.fetchResponse(
                    RESPONDER_URI, subject, issuer, signerKey, signerCert);
            fail("Should fail to query certificate status");
        } finally {
            assertFalse(ServerConf.isCachedOcspResponse(hash(subject)));
        }
    }

    @Test
    public void signatureRequired() throws Exception {
        X509Certificate subject = getDefaultClientCert();

        GlobalConf.reload(new DefaultTestGlobalConf());

        OCSPResp response = OcspTestUtils.createSigRequiredOCSPResponse();

        ocspResponder.setHandler(
                new TestOCSPResponder(response.getEncoded()));
        ocspResponder.start();

        X509Certificate issuer = GlobalConf.getCaCert(subject);
        thrown.expect(OCSPException.class);
        OcspClient.fetchResponse(
                RESPONDER_URI, subject, issuer, null, null);
    }

    // --- utility methods

    @Before
    public void startup() throws Exception {
        ocspResponder = new Server(RESPONDER_PORT);

        KeyConf.reload(new DefaultTestKeyConf());
        ServerConf.reload(new DefaultTestServerConf());

        if (ocspResponderCert == null) {
            ocspResponderCert = TestCertUtil.getOcspSigner().cert;
        }
    }

    @After
    public void shutdown() throws Exception {
        if (ocspResponder != null) {
            try {
                ocspResponder.stop();
            } finally {
                ocspResponder = null;
            }
        }
    }

    private static X509Certificate getDefaultClientCert() throws Exception {
        return TestCertUtil.getTestOrg().cert;
    }

    private static String hash(X509Certificate cert) throws Exception {
        return calculateCertHexHash(cert);
    }

    private class DefaultTestKeyConf extends EmptyKeyConf {
        @Override
        public X509Certificate getOcspSignerCert() throws Exception {
            return TestCertUtil.getOcspSigner().cert;
        }

        @Override
        public PrivateKey getOcspRequestKey(X509Certificate cert)
                throws Exception {
            return TestCertUtil.getOcspSigner().key;
        }
    }

    private class DefaultTestServerConf extends EmptyServerConf {
        Map<String, OCSPResp> ocspResponses = new HashMap<>();

        @Override
        public OCSPResp getOcspResponse(X509Certificate cert) throws Exception {
            return ocspResponses.get(calculateCertHexHash(cert));
        }

        @Override
        public boolean isCachedOcspResponse(String certHash) {
            return ocspResponses.containsKey(certHash);
        }

        @Override
        public void setOcspResponse(String certHash, OCSPResp response) {
            ocspResponses.put(certHash, response);
        }

    }

    private class DefaultTestGlobalConf extends EmptyGlobalConf {
        @Override
        public List<String> getOcspResponderAddresses(X509Certificate org) {
            return Arrays.asList(RESPONDER_URI);
        }

        @Override
        public List<X509Certificate> getOcspResponderCertificates() {
            return Arrays.asList(ocspResponderCert);
        }

        @Override
        public X509Certificate getCaCert(X509Certificate orgCert) {
            return TestCertUtil.getCaCert();
        }
    }

    private static class TestOCSPResponder extends AbstractHandler {

        private final String responseContentType;
        private final byte[] responseData;

        TestOCSPResponder(byte[] response) {
            this("application/ocsp-response", response);
        }

        TestOCSPResponder(String responseContentType, byte[] response) {
            this.responseContentType = responseContentType;
            this.responseData = response;
        }

        @Override
        public void handle(String target, Request baseRequest,
                HttpServletRequest request, HttpServletResponse response)
                throws IOException, ServletException {
            try {
                response.setContentType(responseContentType);
                if (responseData != null) {
                    response.getOutputStream().write(responseData);
                }
            } catch (Exception e) {
                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR_500,
                        e.getMessage());
            } finally {
                baseRequest.setHandled(true);
            }
        }
    }
}
