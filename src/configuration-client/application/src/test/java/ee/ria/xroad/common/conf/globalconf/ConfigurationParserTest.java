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
package ee.ria.xroad.common.conf.globalconf;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.TestCertUtil;

import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.List;

import static ee.ria.xroad.common.ErrorCodes.X_CERT_NOT_FOUND;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_SIGNATURE_VALUE;
import static ee.ria.xroad.common.ErrorCodes.X_MALFORMED_GLOBALCONF;
import static ee.ria.xroad.common.TestExceptionUtils.codedException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests to verify configuration parser functionality.
 */
class ConfigurationParserTest {

    /**
     * Test to ensure the parser succeeds on a simple configuration.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test
    void parseConf() throws Exception {
        List<ConfigurationFile> files =
                parse("src/test/resources/test-conf-simple",
                        getConfigurationSource(
                                TestCertUtil.getConsumer().certChain[0],
                                "EE", "http://foo.bar.baz"));
        assertFiles(files, "/private-params.xml", "/shared-params.xml",
                "/foo.xml");
    }

    /**
     * Test to ensure the parser will fail on a malformed configuration.
     */
    @Test
    void parseMalformedConf() {
        assertThatThrownBy(() ->
                parse("src/test/resources/test-conf-malformed",
                        getConfigurationSource(
                                TestCertUtil.getConsumer().certChain[0],
                                "EE", "http://foo.bar.baz")))
                .is(codedException(X_MALFORMED_GLOBALCONF));
    }

    /**
     * Test to ensure the parser will fail on a missing date.
     */
    @Test
    void parseMalformedConfMissingExpirationDate() {
        assertThatThrownBy(() ->
                parse("src/test/resources/test-conf-missing-date",
                        getConfigurationSource(
                                TestCertUtil.getConsumer().certChain[0],
                                "EE", "http://foo.bar.baz")))
                .is(codedException(X_MALFORMED_GLOBALCONF));
    }

    /**
     * Test to ensure the parser will fail on a missing certificate.
     */
    @Test
    void parseConfWrongVerificationCert() {
        assertThatThrownBy(() ->
                parse("src/test/resources/test-conf-simple",
                        getConfigurationSource(
                                TestCertUtil.getProducer().certChain[0],
                                "EE", "http://foo.bar.baz")))
                .is(codedException(X_CERT_NOT_FOUND));
    }

    /**
     * Test to ensure the parser will fail on an invalid signature.
     */
    @Test
    void parseConfMissingSignature() {
        assertThatThrownBy(() ->
                parse("src/test/resources/test-conf-missing-signature",
                        getConfigurationSource(
                                TestCertUtil.getConsumer().certChain[0],
                                "EE", "http://foo.bar.baz")))
                .is(codedException(X_INVALID_SIGNATURE_VALUE));
    }

    /**
     * Test to ensure the parser will fail on an invalid signature.
     */
    @Test
    void parseConfInvalidSignature() {
        assertThatThrownBy(() ->
                parse("src/test/resources/test-conf-invalid-signature",
                        getConfigurationSource(
                                TestCertUtil.getConsumer().certChain[0],
                                "EE", "http://foo.bar.baz")))
                .is(codedException(X_INVALID_SIGNATURE_VALUE));
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

            assertTrue(found, "Expected file " + expectedFile);
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
                    return List.of(new ConfigurationLocation(instanceIdentifier,
                            downloadURL,
                            List.of(verificationCert.getEncoded())));
                } catch (CertificateEncodingException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }
}
