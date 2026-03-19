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

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.configuration2.INIConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.confproxy.common.domain.ConfProxyInstance;
import org.niis.xroad.confproxy.common.utils.ConfProxyUtils;
import org.niis.xroad.globalconf.model.VersionedConfigurationDirectory;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.client.SignerSignClient;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.niis.xroad.confproxy.common.domain.ConfProxyInstance.VALIDITY_INTERVAL_SECONDS;


/**
 * Test program for the configuration proxy,
 * uses a pre-downloaded configuration.
 */
@Slf4j
@ExtendWith(MockitoExtension.class)
class ConfProxyTest {
    @Mock
    SignerRpcClient signerRpcClient;
    @Mock
    SignerSignClient signerSignClient;
    @Mock
    INIConfiguration configuration;

    @BeforeEach
    void setUp() {
        when(configuration.getInteger(VALIDITY_INTERVAL_SECONDS, -1)).thenReturn(600);
    }

    @Test
    void cleanupTempDirectoriesWhenBuildingSignedDirectoryFails() throws Exception {
        var conf = new ConfProxyInstance("PROXY1", "src/test/resources/conf-proxy-conf", "build/tmp/test/generated-conf", configuration);
        ConfProxyUtils.purgeOutdatedGenerations(conf);

        VersionedConfigurationDirectory confDir = new VersionedConfigurationDirectory("src/test/resources/test-conf-simple/PROXY1/V2");

        when(signerRpcClient.getSignMechanism(any())).thenThrow(XrdRuntimeException.systemInternalError("Signer is unreachable"));

        try (var output = OutputBuilder.build(confDir, 2,
                conf, "address",
                DigestAlgorithm.SHA512, DigestAlgorithm.SHA512,
                "build/tmp/test")) {
            XrdRuntimeException exception = assertThrows(XrdRuntimeException.class,
                    () -> output.buildSignedDirectory(signerRpcClient, signerSignClient));
            assertEquals("Signer is unreachable", exception.getDetails());
        }
        assertEquals(0, Files.list(Paths.get("build/tmp/test/PROXY1")).count());
    }

}
