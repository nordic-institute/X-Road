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
package org.niis.xroad.confproxy.common.service;

import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;

import org.apache.commons.configuration2.INIConfiguration;
import org.bouncycastle.operator.OperatorCreationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.confproxy.common.domain.ConfProxyInstance;
import org.niis.xroad.confproxy.common.exceptions.ConfProxyErrorCode;
import org.niis.xroad.globalconf.model.ConfigurationPartMetadata;
import org.niis.xroad.globalconf.model.FileConsumer;
import org.niis.xroad.globalconf.model.VersionedConfigurationDirectory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateEncodingException;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.niis.xroad.confproxy.common.domain.ConfProxyInstance.VALIDITY_INTERVAL_SECONDS;

@ExtendWith(MockitoExtension.class)
class OutputBuilderPathTraversalTest {

    private static final String CONF_DIR = "src/test/resources/test-conf-simple/PROXY1/V2";
    private static final String CONF_PROXY_CONF = "src/test/resources/conf-proxy-conf";

    @TempDir
    Path tempBase;

    @Mock
    INIConfiguration configuration;

    @BeforeEach
    void setUp() {
        when(configuration.getInteger(VALIDITY_INTERVAL_SECONDS, -1)).thenReturn(600);
    }

    @Test
    void vectorABlankInstanceAndAbsoluteContentLocationIsRejected() throws Exception {
        var conf = confProxyInstance();
        try (var output = OutputBuilder.build(
                new SyntheticConfDir(CONF_DIR, "", "/etc/cron.d/xrd-pwn"), 2, conf, "address",
                DigestAlgorithm.SHA512, DigestAlgorithm.SHA512,
                tempBase.resolve("tmp").toString())) {

            assertThatThrownBy(() -> output.buildSignedDirectory(null, null))
                    .isInstanceOf(XrdRuntimeException.class)
                    .satisfies(ex -> assertThat(((XrdRuntimeException) ex).getErrorCode())
                            .isEqualTo(ConfProxyErrorCode.CONF_PART_PATH_TRAVERSAL.code()));
        }
    }

    @Test
    void vectorBDotDotTraversalInContentLocationIsRejected() throws Exception {
        var conf = confProxyInstance();
        try (var output = OutputBuilder.build(
                new SyntheticConfDir(CONF_DIR, "EE", "/../../../../etc/cron.d/xrd-pwn"), 2, conf, "address",
                DigestAlgorithm.SHA512, DigestAlgorithm.SHA512,
                tempBase.resolve("tmp").toString())) {

            assertThatThrownBy(() -> output.buildSignedDirectory(null, null))
                    .isInstanceOf(XrdRuntimeException.class)
                    .satisfies(ex -> assertThat(((XrdRuntimeException) ex).getErrorCode())
                            .isEqualTo(ConfProxyErrorCode.CONF_PART_PATH_TRAVERSAL.code()));
        }
    }

    @Test
    void legitimateRelativeContentLocationPassesContainmentGuard() throws Exception {
        var conf = confProxyInstance();
        Path tmpDir = tempBase.resolve("tmp");
        try (var output = OutputBuilder.build(
                new SyntheticConfDir(CONF_DIR, "EE", "shared-params.xml"), 2, conf, "address",
                DigestAlgorithm.SHA512, DigestAlgorithm.SHA512,
                tmpDir.toString())) {

            try {
                output.buildSignedDirectory(null, null);
            } catch (XrdRuntimeException e) {
                assertThat(e.getErrorCode()).isNotEqualTo(ConfProxyErrorCode.CONF_PART_PATH_TRAVERSAL.code());
            } catch (Exception e) {
                // signing fails on the null signer client after the part is written; irrelevant to containment
            }

            assertThat(Files.find(tmpDir, 5, (p, a) -> p.getFileName().toString().equals("shared-params.xml"))
                    .findAny())
                    .isPresent();
        }
    }

    private ConfProxyInstance confProxyInstance() {
        return new ConfProxyInstance("PROXY1", CONF_PROXY_CONF,
                tempBase.resolve("generated-conf").toString(), configuration);
    }

    private static class SyntheticConfDir extends VersionedConfigurationDirectory {

        private final String instanceIdentifier;
        private final String contentLocation;

        SyntheticConfDir(String path, String instanceIdentifier, String contentLocation) throws Exception {
            super(path);
            this.instanceIdentifier = instanceIdentifier;
            this.contentLocation = contentLocation;
        }

        @Override
        public synchronized void eachFile(FileConsumer consumer) throws IOException {
            var metadata = new ConfigurationPartMetadata();
            metadata.setContentIdentifier("SHARED-PARAMETERS");
            metadata.setInstanceIdentifier(instanceIdentifier);
            metadata.setContentLocation(contentLocation);
            metadata.setExpirationDate(OffsetDateTime.now().plusHours(1));
            try {
                consumer.consume(metadata, InputStream.nullInputStream());
            } catch (CertificateEncodingException | OperatorCreationException e) {
                throw new IOException(e);
            }
        }
    }
}
