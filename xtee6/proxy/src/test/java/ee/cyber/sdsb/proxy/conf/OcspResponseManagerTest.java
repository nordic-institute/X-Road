package ee.cyber.sdsb.proxy.conf;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.junit.Before;
import org.junit.Test;

import ee.cyber.sdsb.common.OcspTestUtils;
import ee.cyber.sdsb.common.TestCertUtil;
import ee.cyber.sdsb.common.util.CryptoUtils;

/**
 * Tests the OcspResponseManager class. Note that using a mock framework
 * here (for mocking file IO methods) is probably overkill, since we only have
 * only two file IO related methods we do not want to test -- we can simply
 * overload them.
 */
public class OcspResponseManagerTest {

    private boolean responseWrittenToDisk;
    private boolean responseReadFromDisk;

    private class MockedOcspResponseManager extends OcspResponseManager {
        private OCSPResp response;
        public MockedOcspResponseManager(OCSPResp forResponse) {
            response = forResponse;
        }
        @Override
        protected OCSPResp loadResponseFromFile(File file)
                throws IOException {
            assertNotNull(file);
            responseReadFromDisk = true;
            return response;
        }
        @Override
        protected void saveResponseToFile(File file, OCSPResp ocspResponse)
                throws IOException {
            assertNotNull(file);
            assertNotNull(ocspResponse);
            responseWrittenToDisk = true;
        }
    }

    @Before
    public void setUp() {
        responseWrittenToDisk = false;
        responseReadFromDisk = false;
    }

    /**
     * Tests that saving an OCSP response to the manager and retrieving the
     * response from the manager works.
     */
    @Test
    public void testOcspResponseManager() throws Exception {
        X509Certificate cert = TestCertUtil.getTestOrg().cert;
        OCSPResp setResponse = createTestResponse(cert);

        OcspResponseManager manager =
                new MockedOcspResponseManager(setResponse);

        manager.setResponse(hash(cert), setResponse);

        OCSPResp gotResponse = manager.getResponse(hash(cert));
        assertNotNull(gotResponse);
        assertTrue(responseWrittenToDisk);
        assertFalse(responseReadFromDisk);
        assertTrue(Arrays.equals(
                setResponse.getEncoded(), gotResponse.getEncoded()));
    }

    /**
     * Tests that loading a response from file works if the response is not
     * cached in memory.
     */
    @Test
    public void testLoadOcspResponseFromFile() throws Exception {
        X509Certificate cert = TestCertUtil.getTestOrg().cert;
        OCSPResp setResponse = createTestResponse(cert);

        OcspResponseManager manager =
                new MockedOcspResponseManager(setResponse);

        OCSPResp gotResponse = manager.getResponse(hash(cert));
        assertNotNull(gotResponse);
        assertFalse(responseWrittenToDisk);
        assertTrue(responseReadFromDisk);
        assertTrue(Arrays.equals(
                setResponse.getEncoded(), gotResponse.getEncoded()));
    }

    /**
     * Tests that saving a response to file and loading it from file works.
     */
    @Test
    public void testSaveLoadOcspResponseToFile() throws Exception {
        X509Certificate cert = TestCertUtil.getTestOrg().cert;
        OCSPResp setResponse = createTestResponse(cert);

        OcspResponseManager manager =
                new MockedOcspResponseManager(setResponse);

        manager.setResponse(hash(cert), setResponse);

        // clear the cache, so that the manager would look for a file
        manager.clearCache();

        OCSPResp gotResponse = manager.getResponse(hash(cert));
        assertNotNull(gotResponse);
        assertTrue(responseWrittenToDisk);
        assertTrue(responseReadFromDisk);
        assertTrue(Arrays.equals(
                setResponse.getEncoded(), gotResponse.getEncoded()));
    }

    private static String hash(X509Certificate cert) throws Exception {
        return CryptoUtils.calculateCertHexHash(cert);
    }

    private static OCSPResp createTestResponse(X509Certificate cert)
            throws Exception {
        X509Certificate cacert = TestCertUtil.getCaCert();
        X509Certificate signerCert = TestCertUtil.getOcspSigner().cert;
        PrivateKey signerKey = TestCertUtil.getOcspSigner().key;
        return OcspTestUtils.createOCSPResponse(cert, cacert, signerCert,
                signerKey, CertificateStatus.GOOD);
    }
}
