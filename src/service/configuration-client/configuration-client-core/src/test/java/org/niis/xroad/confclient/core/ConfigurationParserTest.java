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
package org.niis.xroad.confclient.core;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;
import ee.ria.xroad.common.util.TimeUtils;

import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.bouncycastle.operator.DigestCalculator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.globalconf.model.ConfigurationLocation;
import org.niis.xroad.globalconf.model.ConfigurationSource;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static ee.ria.xroad.common.TestCertUtil.*;
import static ee.ria.xroad.common.TestExceptionUtils.codedException;
import static ee.ria.xroad.common.crypto.Digests.createDigestCalculator;
import static ee.ria.xroad.common.util.EncoderUtils.encodeBase64;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Tests to verify configuration parser functionality.
 */
@Execution(ExecutionMode.SAME_THREAD)
class ConfigurationParserTest {

    @BeforeEach
    void setClock() {
        TimeUtils.setClock(Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC));
    }

    /**
     * Test to ensure the parser succeeds on a simple configuration.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test
    void parseConf() {
        List<ConfigurationFile> files =
                parse("src/test/resources/test-conf-simple",
                        getConfigurationSource(
                                TestCertUtil.getConsumer().certChain[0],
                                "EE"));
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
                                "EE")))
                .is(codedException(ErrorCode.GLOBAL_CONF_MISSING_SIGNED_DATA_EXPIRATION_DATE.code()));
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
                                "EE")))
                .is(codedException(ErrorCode.GLOBAL_CONF_HEADER_FIELD_MISSING.code()));
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
                                "EE")))
                .is(codedException(ErrorCode.GLOBAL_CONF_MISSING_VERIFICATION_CERT.code()));
    }

    /**
     * Test to ensure the parser will fail on an expired certificate.
     */
    @Test
    void parseConfWrongExpiredVerificationCert() {
        TimeUtils.setClock(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        assertThatThrownBy(() ->
                parse("src/test/resources/test-conf-simple",
                        getConfigurationSource(
                                TestCertUtil.getConsumer().certChain[0],
                                "EE")))
                .is(codedException(ErrorCode.GLOBAL_CONF_MISSING_VERIFICATION_CERT.code()));
    }

    @Test
    void parseConfVerificationCertHashNotCached() {
        parse("src/test/resources/test-conf-simple",
                        getConfigurationSource(
                                TestCertUtil.getConsumer().certChain[0],
                                "EE"));
        assertThatThrownBy(() ->
                parse("src/test/resources/test-conf-simple",
                        getConfigurationSource(
                                TestCertUtil.getProducer().certChain[0],
                                "FI")))
                .is(codedException(ErrorCode.GLOBAL_CONF_MISSING_VERIFICATION_CERT.code()));
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
                                "EE")))
                .is(codedException(ErrorCode.GLOBAL_CONF_SIGNATURE_VERIFICATION_FAILURE.code()));
    }

    /**
     * Test to ensure the parser rejects a validly-signed part whose instance identifier
     * does not match the source that delivered it (cross-instance configuration poisoning).
     */
    @Test
    void parseConfRejectsPartFromForeignInstance() {
        assertThatThrownBy(() ->
                parse("src/test/resources/test-conf-simple",
                        getConfigurationSource(
                                TestCertUtil.getConsumer().certChain[0],
                                "DEV")))
                .is(codedException(ErrorCode.GLOBAL_CONF_PART_INVALID_INSTANCE_IDENTIFIER.code()));
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
                                "EE")))
                .is(codedException(ErrorCode.GLOBAL_CONF_SIGNATURE_VERIFICATION_FAILURE.code()));
    }

    @Test
    void parseConfRejectsNonNumericVersion() {
        assertThatThrownBy(() ->
                parseConfigurationFromBytes(buildSignedConf("not-a-number"),
                        getConfigurationSource(
                                TestCertUtil.getConsumer().certChain[0],
                                "EE")))
                .is(codedException(ErrorCode.GLOBAL_CONF_HEADER_FIELD_WRONG_VALUE.code()));
    }

    @Test
    void parseConfAcceptsNumericVersion() {
        Configuration configuration = parseConfigurationFromBytes(buildSignedConf("3"),
                getConfigurationSource(
                        TestCertUtil.getConsumer().certChain[0],
                        "EE"));
        assertThat(configuration.getVersion()).isEqualTo("3");
    }

    @Test
    void parseConfNormalizesVersionWithSurroundingWhitespace() {
        Configuration configuration = parseConfigurationFromBytes(buildSignedConf(" 3 "),
                getConfigurationSource(
                        TestCertUtil.getConsumer().certChain[0],
                        "EE"));
        assertThat(configuration.getVersion()).isEqualTo("3");
    }

    @Test
    void parseConfTreatsEmptyVersionAsAbsent() {
        Configuration configuration = parseConfigurationFromBytes(buildSignedConf(""),
                getConfigurationSource(
                        TestCertUtil.getConsumer().certChain[0],
                        "EE"));
        assertThat(configuration.getVersion()).isNull();
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
                                                 ConfigurationSource source) {
        if (!source.getLocations().isEmpty()) {
            ConfigurationParser parser = new ConfigurationParser(mock(ConfigurationDownloader.class)) {
                @Override
                @SneakyThrows
                protected InputStream getInputStream() {
                    return new FileInputStream(path + ".txt");
                }
            };

            return parser.parse(source.getLocations().get(0)).getFiles();
        }

        return null;
    }

    private static Configuration parseConfigurationFromBytes(byte[] content,
                                                              ConfigurationSource source) {
        if (!source.getLocations().isEmpty()) {
            ConfigurationParser parser = new ConfigurationParser(mock(ConfigurationDownloader.class)) {
                @Override
                protected InputStream getInputStream() {
                    return new ByteArrayInputStream(content);
                }
            };

            return parser.parse(source.getLocations().getFirst());
        }

        return null;
    }

    private static byte[] buildSignedConf(String version) {
        return buildSignedConf(version, TestCertUtil.getConsumer());
    }

    @SneakyThrows
    private static byte[] buildSignedConf(String version, PKCS12 signCert) {
        String innerBoundary = "--innerboundary\nExpire-date: 2032-05-20T17:42:55Z\nVersion: " + version + "\n\n";

        Signature sig = Signature.getInstance(SignAlgorithm.SHA512_WITH_RSA.name());
        sig.initSign(signCert.key);
        sig.update(innerBoundary.getBytes());

        DigestCalculator dc = createDigestCalculator(DigestAlgorithm.SHA512);
        IOUtils.write(signCert.certChain[0].getEncoded(), dc.getOutputStream());
        String certHash = encodeBase64(dc.getDigest());

        String topMp = "Content-Type: multipart/related; charset=UTF-8;boundary=envelopeboundary\n\n"
                + "--envelopeboundary\n"
                + "Content-Type: multipart/mixed; charset=UTF-8;boundary=innerboundary\n\n"
                + innerBoundary
                + "\n--envelopeboundary\n"
                + "Content-type: application/octet-stream\n"
                + "Content-transfer-encoding: base64\n"
                + "Signature-algorithm-id: http://www.w3.org/2001/04/xmldsig-more#rsa-sha512\n"
                + "Verification-certificate-hash: " + certHash
                + "; hash-algorithm-id=\"http://www.w3.org/2001/04/xmlenc#sha512\"\n"
                + "\n" + encodeBase64(sig.sign()) + "\n"
                + "--envelopeboundary--";

        return topMp.getBytes(StandardCharsets.UTF_8);
    }

    private static ConfigurationSource getConfigurationSource(
            final X509Certificate verificationCert,
            final String instanceIdentifier) {
        return new ConfigurationSource() {

            @Override
            public String getInstanceIdentifier() {
                return instanceIdentifier;
            }

            @Override
            public List<ConfigurationLocation> getLocations() {
                try {
                    return List.of(new ConfigurationLocation(instanceIdentifier,
                            "http://foo.bar.baz",
                            List.of(verificationCert.getEncoded())));
                } catch (CertificateEncodingException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }
}
