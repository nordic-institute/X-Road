package ee.cyber.sdsb.common.conf.globalconf;

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;

import ee.cyber.sdsb.common.ExpectedCodedException;
import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.TestCertUtil;

import static ee.cyber.sdsb.common.ErrorCodes.*;
import static org.junit.Assert.assertTrue;

public class ConfigurationParserTest {

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    @Test
    public void parseConf() throws Exception {
        List<ConfigurationFile> files =
                parse("src/test/resources/test-conf-simple",
                        getConfigurationSource(
                                TestCertUtil.getConsumer().cert,
                                "EE", "http://foo.bar.baz"));
        assertFiles(files, "/private-params.xml", "/shared-params.xml",
                "/foo.xml");
    }

    @Test
    public void parseMalformedConf() throws Exception {
        thrown.expectError(X_MALFORMED_GLOBALCONF);

        parse("src/test/resources/test-conf-malformed",
                getConfigurationSource(
                        TestCertUtil.getConsumer().cert,
                        "EE", "http://foo.bar.baz"));
    }

    @Test
    public void parseMalformedConfMissingExpirationDate() throws Exception {
        thrown.expectError(X_MALFORMED_GLOBALCONF);

        parse("src/test/resources/test-conf-missing-date",
                getConfigurationSource(
                        TestCertUtil.getConsumer().cert,
                        "EE", "http://foo.bar.baz"));
    }

    @Test
    public void parseConfWrongVerificationCert() throws Exception {
        thrown.expectError(X_CERT_NOT_FOUND);

        parse("src/test/resources/test-conf-simple",
                getConfigurationSource(
                        TestCertUtil.getProducer().cert,
                        "EE", "http://foo.bar.baz"));
    }

    @Test
    public void parseConfMissingSignature() throws Exception {
        thrown.expectError(X_INVALID_SIGNATURE_VALUE);

        parse("src/test/resources/test-conf-missing-signature",
                getConfigurationSource(
                        TestCertUtil.getConsumer().cert,
                        "EE", "http://foo.bar.baz"));
    }

    // ------------------------------------------------------------------------

    private static void assertFiles(List<ConfigurationFile> actualFiles,
            String... expectedFiles) {
        for (String expectedFile : expectedFiles) {
            boolean found = false;
            for (ConfigurationFile actualFile : actualFiles) {
                if (actualFile.getContentLocation().equals(expectedFile)) {
                    found = true;
                    break;
                }
            }

            assertTrue("Expected file " + expectedFile, found);
        }
    }

    private static List<ConfigurationFile> parse(final String path,
            ConfigurationSource cource) throws Exception {
        System.setProperty(SystemProperties.CONFIGURATION_PATH, path);

        ConfigurationParser.HASH_TO_CERT.clear();

        ConfigurationParser parser = new ConfigurationParser() {
            @Override
            protected InputStream getInputStream(String downloadURL)
                    throws Exception {
                return new FileInputStream(path + ".txt");
            }
        };

        for (ConfigurationLocation location : cource.getLocations()) {
            return parser.parse(location);
        }

        return null;
    }

    private static ConfigurationSource getConfigurationSource(
            final X509Certificate verificationCert,
            final String instanceIdentifier, final String downloadURL) {
        return new ConfigurationSource() {

            @Override
            public String getInstanceIdentifier() {
                return instanceIdentifier;
            }

            @Override
            public List<ConfigurationLocation> getLocations() {
                try {
                    return Arrays.asList(new ConfigurationLocation(this,
                            downloadURL,
                            Arrays.asList(verificationCert.getEncoded())));
                } catch (CertificateEncodingException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }
}
