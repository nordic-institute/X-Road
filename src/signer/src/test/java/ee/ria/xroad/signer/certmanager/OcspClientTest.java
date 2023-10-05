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
package ee.ria.xroad.signer.certmanager;

import ee.ria.xroad.common.OcspTestUtils;
import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;
import ee.ria.xroad.common.ocsp.OcspVerifier;
import ee.ria.xroad.common.ocsp.OcspVerifierOptions;
import ee.ria.xroad.common.util.TimeUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPException;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.ConnectException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ee.ria.xroad.common.util.CryptoUtils.calculateCertHexHash;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests the OCSP client.
 */
public class OcspClientTest {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private static final int RESPONDER_PORT = 8091;

    private static final String RESPONDER_URI = "http://127.0.0.1:" + RESPONDER_PORT;

    private static Server ocspResponder;
    private static byte[] responseData;

    private static final Map<String, OCSPResp> OCSP_RESPONSES = new HashMap<>();
    private static X509Certificate ocspResponderCert;

    private OcspClientWorker ocspClient;

    // --- test cases

    /**
     * Test.
     *
     * @throws Exception if an error occurs
     */
    @Test
    public void goodCertificateStatus() throws Exception {
        X509Certificate subject = getDefaultClientCert();

        GlobalConf.reload(getTestGlobalConf());

        Date thisUpdate = Date.from(TimeUtils.now().plus(1, ChronoUnit.DAYS));

        responseData = OcspTestUtils.createOCSPResponse(subject, GlobalConf.getCaCert("EE", subject), ocspResponderCert,
                getOcspSignerKey(), CertificateStatus.GOOD, thisUpdate, null).getEncoded();

        queryAndUpdateCertStatus(ocspClient, subject);

        OCSPResp ocsp = getOcspResponse(subject);
        assertNotNull(ocsp);

        OcspVerifier verifier = new OcspVerifier(GlobalConf.getOcspFreshnessSeconds(),
                new OcspVerifierOptions(true));
        verifier.verifyValidityAndStatus(ocsp, subject, GlobalConf.getCaCert("EE", subject));
    }

    /**
     * Test.
     *
     * @throws Exception if an error occurs
     */
    @Test
    public void goodCertificateStatusFromSecondResponder() throws Exception {
        X509Certificate subject = getDefaultClientCert();

        GlobalConfProvider conf = getTestGlobalConf();
        when(conf.getOcspResponderAddresses(Mockito.any(X509Certificate.class))).thenReturn(
                Arrays.asList("http://127.0.0.1:1234", RESPONDER_URI));
        GlobalConf.reload(conf);

        Date thisUpdate = Date.from(TimeUtils.now().plus(1, ChronoUnit.DAYS));

        responseData = OcspTestUtils.createOCSPResponse(subject, GlobalConf.getCaCert("EE", subject), ocspResponderCert,
                getOcspSignerKey(), CertificateStatus.GOOD, thisUpdate, null).getEncoded();

        queryAndUpdateCertStatus(ocspClient, subject);

        OCSPResp ocsp = getOcspResponse(subject);
        assertNotNull(ocsp);

        OcspVerifier verifier = new OcspVerifier(GlobalConf.getOcspFreshnessSeconds(),
                new OcspVerifierOptions(true));
        verifier.verifyValidityAndStatus(ocsp, subject, GlobalConf.getCaCert("EE", subject));
    }

    /**
     * Test.
     *
     * @throws Exception if an error occurs
     */
    @Test
    public void noResponseFromOCSPServer() throws Exception {
        X509Certificate subject = getDefaultClientCert();

        GlobalConf.reload(getTestGlobalConf());

        responseData = null;

        X509Certificate issuer = GlobalConf.getCaCert("EE", subject);

        thrown.expect(IOException.class);

        OcspClient.fetchResponse(RESPONDER_URI, subject, issuer, null, null, null);
    }

    /**
     * Test.
     *
     * @throws Exception if an error occurs
     */
    @Test
    public void faultyResponseFromOCSPServer() throws Exception {
        X509Certificate subject = getDefaultClientCert();

        GlobalConf.reload(getTestGlobalConf());

        responseData = "abcdefgh".getBytes();

        X509Certificate issuer = GlobalConf.getCaCert("EE", subject);

        thrown.expect(OCSPException.class);

        OcspClient.fetchResponse(RESPONDER_URI, subject, issuer, null, null, null);
    }

    /**
     * Test.
     *
     * @throws Exception if an error occurs
     */
    @Test
    public void cannotConnectNoResponders() throws Exception {
        // this certificate does not contain responder URI in AIA extension.
        X509Certificate subject = TestCertUtil.getCertChainCert("user_0.p12");

        GlobalConfProvider conf = getTestGlobalConf();
        when(conf.getOcspResponderAddresses(Mockito.any(X509Certificate.class))).thenReturn(new ArrayList<>());
        GlobalConf.reload(conf);

        thrown.expect(ConnectException.class);

        queryAndUpdateCertStatus(ocspClient, subject);
        fail("Should fail to query certificate status");
    }

    /**
     * Test.
     *
     * @throws Exception if an error occurs
     */
    @Test
    public void cannotConnect() throws Exception {
        X509Certificate subject = getDefaultClientCert();

        GlobalConf.reload(getTestGlobalConf());

        X509Certificate issuer = GlobalConf.getCaCert("EE", subject);

        thrown.expect(IOException.class);

        OcspClient.fetchResponse("foobar", subject, issuer, null, null, null);
        fail("Should fail to query certificate status");
    }

    /**
     * Test.
     *
     * @throws Exception if an error occurs
     */
    @Test
    public void signatureRequired() throws Exception {
        X509Certificate subject = getDefaultClientCert();

        GlobalConf.reload(getTestGlobalConf());

        responseData = OcspTestUtils.createSigRequiredOCSPResponse().getEncoded();

        X509Certificate issuer = GlobalConf.getCaCert("EE", subject);

        thrown.expect(OCSPException.class);

        OcspClient.fetchResponse(RESPONDER_URI, subject, issuer, null, null, null);
    }

    // ------------------------------------------------------------------------

    /**
     * BeforeClass
     *
     * @throws Exception if an error occurs
     */
    @BeforeClass
    public static void doBeforeClass() throws Exception {
        ocspResponder = new Server(RESPONDER_PORT);
        ocspResponder.setHandler(new TestOCSPResponder());
        ocspResponder.start();
    }

    /**
     * Before
     *
     * @throws Exception if an error occurs
     */
    @Before
    public void startup() {
        OCSP_RESPONSES.clear();

        if (ocspResponderCert == null) {
            ocspResponderCert = TestCertUtil.getOcspSigner().certChain[0];
        }

        OcspResponseManager ocspResponseManager = new OcspResponseManager();
        ocspClient = new TestOcspClient(ocspResponseManager);
    }

    /**
     * AfterClass
     *
     * @throws Exception if an error occurs
     */
    @AfterClass
    public static void shutdown() throws Exception {
        if (ocspResponder != null) {
            try {
                ocspResponder.stop();
            } finally {
                ocspResponder = null;
            }
        }
    }

    private static X509Certificate getDefaultClientCert() {
        return TestCertUtil.getConsumer().certChain[0];
    }

    private static String hash(X509Certificate cert) throws Exception {
        return calculateCertHexHash(cert);
    }

    private static PrivateKey getOcspSignerKey() {
        return TestCertUtil.getOcspSigner().key;
    }

    private GlobalConfProvider getTestGlobalConf() throws Exception {
        GlobalConfProvider testConf = mock(GlobalConfProvider.class);

        when(testConf.getInstanceIdentifier()).thenReturn("TEST");

        when(testConf.getOcspResponderAddresses(Mockito.any(X509Certificate.class))).thenReturn(
                List.of(RESPONDER_URI));

        when(testConf.getOcspResponderCertificates()).thenReturn(List.of(ocspResponderCert));

        when(testConf.getCaCert(Mockito.any(String.class), Mockito.any(X509Certificate.class))).thenReturn(
                TestCertUtil.getCaCert());

        when(testConf.isOcspResponderCert(Mockito.any(X509Certificate.class),
                Mockito.any(X509Certificate.class))).thenReturn(true);

        return testConf;
    }

    private void queryAndUpdateCertStatus(OcspClientWorker client, X509Certificate subject) throws Exception {
        OCSPResp response = client.queryCertStatus(subject, new OcspVerifierOptions(true));
        String subjectHash = calculateCertHexHash(subject);
        OCSP_RESPONSES.put(subjectHash, response);
    }

    private OCSPResp getOcspResponse(X509Certificate subject) throws Exception {
        return OCSP_RESPONSES.get(hash(subject));
    }

    private static class TestOcspClient extends OcspClientWorker {
        TestOcspClient(OcspResponseManager ocspResponseManager) {
            super(ocspResponseManager);
        }

        @Override
        void updateCertStatuses(Map<String, OCSPResp> statuses) {
            OCSP_RESPONSES.putAll(statuses);
        }
    }

    private static class TestOCSPResponder extends AbstractHandler {

        private final String responseContentType = "application/ocsp-response";

        @Override
        public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
                throws IOException {
            try {
                response.setContentType(responseContentType);

                if (responseData != null) {
                    response.getOutputStream().write(responseData);
                }
            } catch (Exception e) {
                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR_500, e.getMessage());
            } finally {
                baseRequest.setHandled(true);
            }
        }
    }
}
