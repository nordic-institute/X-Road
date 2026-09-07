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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.confproxy.common.domain.ConfProxyInstance;
import org.niis.xroad.globalconf.model.ConfigurationConstants;
import org.niis.xroad.globalconf.model.ConfigurationDirectory;
import org.niis.xroad.globalconf.model.ConfigurationPartMetadata;
import org.niis.xroad.globalconf.model.VersionedConfigurationDirectory;

import java.nio.file.Path;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.niis.xroad.confproxy.common.domain.ConfProxyInstance.VALIDITY_INTERVAL_SECONDS;

@ExtendWith(MockitoExtension.class)
class OutputBuilderVersionGuardTest {

    private static final String CONF_PROXY_CONF = "src/test/resources/conf-proxy-conf";
    private static final String INSTANCE_IDENTIFIER = "EE";

    @TempDir
    Path tempBase;

    @Mock
    INIConfiguration configuration;

    @BeforeEach
    void setUp() {
        when(configuration.getInteger(VALIDITY_INTERVAL_SECONDS, -1)).thenReturn(600);
    }

    @Test
    void nonNumericConfigurationVersionSurfacesWithItsOwnErrorCode() throws Exception {
        var confDir = confDirWithSharedParams("not-a-number");
        var conf = confProxyInstance();
        try (var output = OutputBuilder.build(confDir, 2, conf, "address",
                DigestAlgorithm.SHA512, DigestAlgorithm.SHA512,
                tempBase.resolve("tmp").toString())) {

            assertThatThrownBy(() -> output.buildSignedDirectory(null, null))
                    .isInstanceOf(XrdRuntimeException.class)
                    .satisfies(ex -> assertThat(((XrdRuntimeException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.GLOBAL_CONF_HEADER_FIELD_WRONG_VALUE.code()));
        }
    }

    @Test
    void negativeConfigurationVersionIsRejected() throws Exception {
        var confDir = confDirWithSharedParams("-1");
        var conf = confProxyInstance();
        try (var output = OutputBuilder.build(confDir, 2, conf, "address",
                DigestAlgorithm.SHA512, DigestAlgorithm.SHA512,
                tempBase.resolve("tmp").toString())) {

            assertThatThrownBy(() -> output.buildSignedDirectory(null, null))
                    .isInstanceOf(XrdRuntimeException.class)
                    .satisfies(ex -> assertThat(((XrdRuntimeException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.GLOBAL_CONF_HEADER_FIELD_WRONG_VALUE.code()));
        }
    }

    @Test
    void nullConfigurationVersionDoesNotOverrideConfigurationSources() throws Exception {
        var confDir = confDirWithSharedParams(null);
        var conf = confProxyInstance();
        try (var output = OutputBuilder.build(confDir, 2, conf, "address",
                DigestAlgorithm.SHA512, DigestAlgorithm.SHA512,
                tempBase.resolve("tmp").toString())) {

            try {
                output.buildSignedDirectory(null, null);
            } catch (XrdRuntimeException e) {
                assertThat(e.getErrorCode()).isNotEqualTo(ErrorCode.GLOBAL_CONF_HEADER_FIELD_WRONG_VALUE.code());
            } catch (Exception e) {
                // signing fails on the null signer client after the part is written; irrelevant to the version guard
            }
        }
    }

    private VersionedConfigurationDirectory confDirWithSharedParams(String configurationVersion) throws Exception {
        Path confRoot = tempBase.resolve("conf");
        ConfigurationDirectory.saveInstanceIdentifier(confRoot.toString(), INSTANCE_IDENTIFIER);

        Path sharedParamsPath = confRoot.resolve(INSTANCE_IDENTIFIER).resolve(ConfigurationConstants.FILE_NAME_SHARED_PARAMETERS);
        var metadata = new ConfigurationPartMetadata();
        metadata.setContentIdentifier(ConfigurationConstants.CONTENT_ID_SHARED_PARAMETERS);
        metadata.setInstanceIdentifier(INSTANCE_IDENTIFIER);
        metadata.setContentLocation("/shared-params.xml");
        metadata.setExpirationDate(OffsetDateTime.now().plusHours(1));
        metadata.setConfigurationVersion(configurationVersion);

        ConfigurationDirectory.save(sharedParamsPath, "<shared-params/>".getBytes(), metadata);

        return new VersionedConfigurationDirectory(confRoot.toString());
    }

    private ConfProxyInstance confProxyInstance() {
        return new ConfProxyInstance("PROXY1", CONF_PROXY_CONF,
                tempBase.resolve("generated-conf").toString(), configuration);
    }
}
