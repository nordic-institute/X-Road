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
package org.niis.xroad.signer.core.certmanager;

import ee.ria.xroad.common.OcspTestUtils;
import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.util.TimeUtils;

import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPException;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.Callback;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.extension.GlobalConfExtensions;
import org.niis.xroad.globalconf.impl.FileSystemGlobalConfSource;
import org.niis.xroad.globalconf.impl.extension.GlobalConfExtensionFactoryImpl;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifier;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifierOptions;
import org.niis.xroad.signer.core.config.SignerProperties;
import org.niis.xroad.signer.core.tokenmanager.TokenManager;

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

import static ee.ria.xroad.common.SystemProperties.getConfigurationPath;
import static ee.ria.xroad.common.util.CryptoUtils.calculateCertHexHash;
import static ee.ria.xroad.common.util.JettyUtils.setContentType;
import static org.eclipse.jetty.io.Content.Sink.asOutputStream;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.properties.ConfigUtils.defaultConfiguration;

/**
 * Tests the OCSP client.
 */
class OcspClientTest {
    private static final int RESPONDER_PORT = 8091;

    private static final String RESPONDER_URI = "http://127.0.0.1:" + RESPONDER_PORT;

    private static Server ocspResponder;
    private static byte[] responseData;

    private static final Map<String, OCSPResp> OCSP_RESPONSES = new HashMap<>();
    private static X509Certificate ocspResponderCert;

    private final GlobalConfProvider globalConfProvider = globalConfProvider();
    private final OcspClient ocspClient = new OcspClient(globalConfProvider);
    private final TokenManager tokenManager = mock(TokenManager.class);
    private final OcspClientWorker ocspClientWorker = new TestOcspClient(globalConfProvider,
            new OcspResponseManager(globalConfProvider, tokenManager, ocspClient,
                    new FileBasedOcspCache(globalConfProvider, defaultConfiguration(SignerProperties.class))),
            tokenManager, ocspClient);

    OcspClientTest() throws Exception {
    }

    /**
     * Test.
     *
     * @throws Exception if an error occurs
     */
    @Test
    void goodCertificateStatus() throws Exception {
        X509Certificate subject = getDefaultClientCert();

        Date thisUpdate = Date.from(TimeUtils.now().plus(1, ChronoUnit.DAYS));

        responseData = OcspTestUtils.createOCSPResponse(subject, globalConfProvider.getCaCert("EE", subject),
                ocspResponderCert, getOcspSignerKey(), CertificateStatus.GOOD, thisUpdate, null).getEncoded();

        queryAndUpdateCertStatus(ocspClientWorker, subject);

        OCSPResp ocsp = getOcspResponse(subject);
        assertNotNull(ocsp);

        OcspVerifier verifier = new OcspVerifier(globalConfProvider,
                new OcspVerifierOptions(true));
        verifier.verifyValidityAndStatus(ocsp, subject, globalConfProvider.getCaCert("EE", subject));
    }

    /**
     * Test.
     *
     * @throws Exception if an error occurs
     */
    @Test
    void goodCertificateStatusFromSecondResponder() throws Exception {
        X509Certificate subject = getDefaultClientCert();

        when(globalConfProvider.getOcspResponderAddresses(Mockito.any(X509Certificate.class))).thenReturn(
                Arrays.asList("http://127.0.0.1:1234", RESPONDER_URI));

        Date thisUpdate = Date.from(TimeUtils.now().plus(1, ChronoUnit.DAYS));

        responseData = OcspTestUtils.createOCSPResponse(subject, globalConfProvider.getCaCert("EE", subject),
                ocspResponderCert, getOcspSignerKey(), CertificateStatus.GOOD, thisUpdate, null).getEncoded();

        queryAndUpdateCertStatus(ocspClientWorker, subject);

        OCSPResp ocsp = getOcspResponse(subject);
        assertNotNull(ocsp);

        OcspVerifier verifier = new OcspVerifier(globalConfProvider,
                new OcspVerifierOptions(true));
        verifier.verifyValidityAndStatus(ocsp, subject, globalConfProvider.getCaCert("EE", subject));
    }

    /**
     * Test.
     *
     * @throws Exception if an error occurs
     */
    @Test
    void noResponseFromOCSPServer() throws Exception {
        X509Certificate subject = getDefaultClientCert();

        responseData = null;

        X509Certificate issuer = globalConfProvider.getCaCert("EE", subject);

        assertThrows(IOException.class, () ->
                ocspClient.fetchResponse(RESPONDER_URI, subject, issuer, null, null, null));
    }

    /**
     * Test.
     *
     * @throws Exception if an error occurs
     */
    @Test
    void faultyResponseFromOCSPServer() throws Exception {
        X509Certificate subject = getDefaultClientCert();

        responseData = "abcdefgh".getBytes();

        X509Certificate issuer = globalConfProvider.getCaCert("EE", subject);

        assertThrows(OCSPException.class, () ->
                ocspClient.fetchResponse(RESPONDER_URI, subject, issuer, null, null, null));
    }

    /**
     * Test.
     *
     * @throws Exception if an error occurs
     */
    @Test
    void cannotConnectNoResponders() throws Exception {
        // this certificate does not contain responder URI in AIA extension.
        X509Certificate subject = TestCertUtil.getCertChainCert("user_0.p12");

        when(globalConfProvider.getOcspResponderAddresses(Mockito.any(X509Certificate.class))).thenReturn(new ArrayList<>());

        assertThrows(ConnectException.class, () -> queryAndUpdateCertStatus(ocspClientWorker, subject));
    }

    /**
     * Test.
     *
     * @throws Exception if an error occurs
     */
    @Test
    void cannotConnect() throws Exception {
        X509Certificate subject = getDefaultClientCert();

        X509Certificate issuer = globalConfProvider.getCaCert("EE", subject);

        assertThrows(IOException.class, () ->
                ocspClient.fetchResponse("foobar", subject, issuer, null, null, null));
    }

    /**
     * Test.
     *
     * @throws Exception if an error occurs
     */
    @Test
    void signatureRequired() throws Exception {
        X509Certificate subject = getDefaultClientCert();

        responseData = OcspTestUtils.createSigRequiredOCSPResponse().getEncoded();

        X509Certificate issuer = globalConfProvider.getCaCert("EE", subject);

        assertThrows(OCSPException.class, () ->
                ocspClient.fetchResponse(RESPONDER_URI, subject, issuer, null, null, null));
    }

    // ------------------------------------------------------------------------

    /**
     * BeforeClass
     *
     * @throws Exception if an error occurs
     */
    @BeforeAll
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
    @BeforeEach
    public void startup() {
        OCSP_RESPONSES.clear();

//        if (ocspResponderCert == null) {
//            ocspResponderCert = TestCertUtil.getOcspSigner().certChain[0];
//        }
    }

    /**
     * AfterClass
     *
     * @throws Exception if an error occurs
     */
    @AfterAll
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

    private void queryAndUpdateCertStatus(OcspClientWorker client, X509Certificate subject) throws Exception {
        OCSPResp response = client.queryCertStatus(subject, new OcspVerifierOptions(true));
        String subjectHash = calculateCertHexHash(subject);
        OCSP_RESPONSES.put(subjectHash, response);
    }

    private OCSPResp getOcspResponse(X509Certificate subject) throws Exception {
        return OCSP_RESPONSES.get(hash(subject));
    }

    private static class TestOcspClient extends OcspClientWorker {
        TestOcspClient(GlobalConfProvider globalConfProvider, OcspResponseManager ocspResponseManager,
                       TokenManager tokenManager, OcspClient ocspClient) {
            super(globalConfProvider, ocspResponseManager, tokenManager, ocspClient);
        }

        @Override
        void updateCertStatuses(Map<String, OCSPResp> statuses) {
            OCSP_RESPONSES.putAll(statuses);
        }
    }

    private static final class TestOCSPResponder extends Handler.Abstract {

        private final String responseContentType = "application/ocsp-response";

        @Override
        public boolean handle(Request request, Response response, Callback callback) {
            try {
                setContentType(response, responseContentType);

                if (responseData != null) {
                    asOutputStream(response).write(responseData);
                }
                callback.succeeded();
            } catch (Exception e) {
                Response.writeError(request, response, callback, HttpStatus.INTERNAL_SERVER_ERROR_500, e.getMessage());
            }
            return true;
        }
    }

    private GlobalConfProvider globalConfProvider() throws Exception {
        GlobalConfProvider testConf = mock(GlobalConfProvider.class);

        when(testConf.getInstanceIdentifier()).thenReturn("TEST");

        when(testConf.getOcspResponderAddresses(Mockito.any(X509Certificate.class))).thenReturn(
                List.of(RESPONDER_URI));

        ocspResponderCert = TestCertUtil.getOcspSigner().certChain[0];
        when(testConf.getOcspResponderCertificates()).thenReturn(List.of(ocspResponderCert));

        when(testConf.getCaCert(Mockito.any(String.class), Mockito.any(X509Certificate.class))).thenReturn(
                TestCertUtil.getCaCert());

        when(testConf.isOcspResponderCert(Mockito.any(X509Certificate.class),
                Mockito.any(X509Certificate.class))).thenReturn(true);

        FileSystemGlobalConfSource source = new FileSystemGlobalConfSource(getConfigurationPath());
        when(testConf.getGlobalConfExtensions()).thenReturn(new GlobalConfExtensions(source, new GlobalConfExtensionFactoryImpl()));
        return testConf;
    }
}
