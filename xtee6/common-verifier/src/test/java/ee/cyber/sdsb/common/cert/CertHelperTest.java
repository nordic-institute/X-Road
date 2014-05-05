package ee.cyber.sdsb.common.cert;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.FileInputStream;
import java.security.cert.X509Certificate;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import ee.cyber.sdsb.common.TestCertUtil;
import ee.cyber.sdsb.common.util.CryptoUtils;

public class CertHelperTest {

    @Test
    public void getSubjectCommonName() {
        X509Certificate cert = TestCertUtil.getProducer().cert;
        String commonName = CertHelper.getSubjectCommonName(cert);
        assertEquals("producer", commonName);
    }

    @Test
    public void getSubjectSerialNumber() throws Exception {
        String base64data = IOUtils.toString(new FileInputStream(
                        "../common-test/src/test/certs/test-esteid.txt"));
        X509Certificate cert = CryptoUtils.readCertificate(base64data);
        String serialNumber = CertHelper.getSubjectSerialNumber(cert);
        assertEquals("47101010033", serialNumber);
    }

    @Test
    public void subjectSerialNumberNotAvailable() throws Exception {
        X509Certificate cert = TestCertUtil.getProducer().cert;
        String serialNumber = CertHelper.getSubjectSerialNumber(cert);
        assertNull(serialNumber);
    }
}
