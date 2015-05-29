package ee.ria.xroad.common.cert;

import java.io.FileInputStream;
import java.security.cert.X509Certificate;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.util.CryptoUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Tests the CertHelper utility class.
 */
public class CertHelperTest {

    /**
     * Tests that the name extractor returns correct name from certificate.
     */
    @Test
    public void getSubjectCommonName() {
        X509Certificate cert = TestCertUtil.getProducer().cert;
        String commonName = CertHelper.getSubjectCommonName(cert);
        assertEquals("producer", commonName);
    }

    /**
     * Tests getting the subject serial number from the certificate.
     * @throws Exception if an error occurs
     */
    @Test
    public void getSubjectSerialNumber() throws Exception {
        String base64data = IOUtils.toString(new FileInputStream(
                        "../common-test/src/test/certs/test-esteid.txt"));
        X509Certificate cert = CryptoUtils.readCertificate(base64data);
        String serialNumber = CertHelper.getSubjectSerialNumber(cert);
        assertEquals("47101010033", serialNumber);
    }

    /**
     * Tests that getting serial number from a certificate which does not have
     * one returns null.
     * @throws Exception if an error occurs
     */
    @Test
    public void subjectSerialNumberNotAvailable() throws Exception {
        X509Certificate cert = TestCertUtil.getProducer().cert;
        String serialNumber = CertHelper.getSubjectSerialNumber(cert);
        assertNull(serialNumber);
    }
}
