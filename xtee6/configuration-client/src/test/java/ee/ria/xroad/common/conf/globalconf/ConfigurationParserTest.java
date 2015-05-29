package ee.ria.xroad.common.conf.globalconf;

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;

import ee.ria.xroad.common.ExpectedCodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.TestCertUtil;

import static ee.ria.xroad.common.ErrorCodes.*;
import static org.junit.Assert.assertTrue;

/**
 * Tests to verify configuration parser functionality.
 */
public class ConfigurationParserTest {

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    /**
     * Test to ensure the parser succeeds on a simple configuration.
     * @throws Exception in case of any unexpected errors
     */
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

    /**
     * Test to ensure the parser will fail on a malformed configuration.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void parseMalformedConf() throws Exception {
        thrown.expectError(X_MALFORMED_GLOBALCONF);

        parse("src/test/resources/test-conf-malformed",
                getConfigurationSource(
                        TestCertUtil.getConsumer().cert,
                        "EE", "http://foo.bar.baz"));
    }

    /**
     * Test to ensure the parser will fail on a missing date.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void parseMalformedConfMissingExpirationDate() throws Exception {
        thrown.expectError(X_MALFORMED_GLOBALCONF);

        parse("src/test/resources/test-conf-missing-date",
                getConfigurationSource(
                        TestCertUtil.getConsumer().cert,
                        "EE", "http://foo.bar.baz"));
    }

    /**
     * Test to ensure the parser will fail on a missing certificate.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void parseConfWrongVerificationCert() throws Exception {
        thrown.expectError(X_CERT_NOT_FOUND);

        parse("src/test/resources/test-conf-simple",
                getConfigurationSource(
                        TestCertUtil.getProducer().cert,
                        "EE", "http://foo.bar.baz"));
    }

    /**
     * Test to ensure the parser will fail on an invalid signature.
     * @throws Exception in case of any unexpected errors
     */
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
            ConfigurationSource source) throws Exception {
        System.setProperty(SystemProperties.CONFIGURATION_PATH, path);

        ConfigurationParser.HASH_TO_CERT.clear();

        if (!source.getLocations().isEmpty()) {
            ConfigurationParser parser = new ConfigurationParser() {
                @Override
                protected InputStream getInputStream() throws Exception {
                    return new FileInputStream(path + ".txt");
                }
            };

            return parser.parse(source.getLocations().get(0)).getFiles();
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
