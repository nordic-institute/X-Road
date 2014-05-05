package ee.cyber.sdsb.common.signature;

import java.io.File;
import java.io.FileInputStream;
import java.security.cert.X509Certificate;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.bouncycastle.tsp.TimeStampToken;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import ee.cyber.sdsb.common.ErrorCodes;
import ee.cyber.sdsb.common.ExpectedCodedException;
import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.TestSecurityUtil;
import ee.cyber.sdsb.common.conf.GlobalConf;

import static org.junit.Assert.assertNotNull;

public class TimestampVerifierTest {

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    @BeforeClass
    public static void setUpBeforeClass() {
        TestSecurityUtil.initSecurity();

        System.setProperty(SystemProperties.GLOBAL_CONFIGURATION_FILE,
                "src/test/globalconftest.xml");
        GlobalConf.reload();
    }

    @Test
    public void validTimestamp() throws Exception {
        TimeStampToken token = getTimestampFromFile("valid");
        byte[] stampedData = getBytesFromFile("stamped-data");
        List<X509Certificate> tspCerts = GlobalConf.getTspCertificates();
        TimestampVerifier.verify(token, stampedData, tspCerts);
    }

    @Test
    public void hashMismatch() throws Exception {
        thrown.expectError(ErrorCodes.X_MALFORMED_SIGNATURE);
        TimeStampToken token = getTimestampFromFile("valid");
        byte[] stampedData = getBytesFromFile("stamped-data");
        stampedData[42] = 0x01; // change a byte
        TimestampVerifier.verify(token, stampedData, null);
    }

    @Test
    public void wrongCertificate() throws Exception {
        thrown.expectError(ErrorCodes.X_INTERNAL_ERROR);
        TimeStampToken token = getTimestampFromFile("valid");
        byte[] stampedData = getBytesFromFile("stamped-data");
        List<X509Certificate> tspCerts =
                GlobalConf.getOcspResponderCertificates(); // use ocsp certs
        TimestampVerifier.verify(token, stampedData, tspCerts);
    }

    @Test
    public void invalidSignature() throws Exception {
        thrown.expectError(ErrorCodes.X_TIMESTAMP_VALIDATION);
        TimeStampToken token = getTimestampFromFile("invalid-signature");
        byte[] stampedData = getBytesFromFile("stamped-data");
        List<X509Certificate> tspCerts = GlobalConf.getTspCertificates();
        TimestampVerifier.verify(token, stampedData, tspCerts);
    }

    private static TimeStampToken getTimestampFromFile(String fileName)
            throws Exception {
        byte[] data = getBytesFromFile(fileName);
        TimeStampToken token = new TimeStampToken(new ContentInfo(
                (ASN1Sequence) ASN1Sequence.fromByteArray(data)));
        assertNotNull(token);
        return token;
    }

    private static byte[] getBytesFromFile(String fileName)
            throws Exception {
        File file = new File("src/test/timestamps/" + fileName);
        return IOUtils.toByteArray(new FileInputStream(file));
    }
}
