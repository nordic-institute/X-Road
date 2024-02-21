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
package ee.ria.xroad.confproxy;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.conf.globalconf.VersionedConfigurationDirectory;
import ee.ria.xroad.confproxy.util.ConfProxyHelper;
import ee.ria.xroad.confproxy.util.OutputBuilder;
import ee.ria.xroad.signer.SignerProxy;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.nio.file.Files;
import java.nio.file.Paths;

import static ee.ria.xroad.common.SystemProperties.CONFIGURATION_PATH;
import static ee.ria.xroad.common.SystemProperties.CONFIGURATION_PROXY_CONF_PATH;
import static ee.ria.xroad.common.SystemProperties.CONFIGURATION_PROXY_GENERATED_CONF_PATH;
import static ee.ria.xroad.common.SystemProperties.TEMP_FILES_PATH;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

/**
 * Test program for the configuration proxy,
 * uses a pre-downloaded configuration.
 */
@Slf4j
public class ConfProxyTest {

    @Before
    public void setUp() {
        System.setProperty(CONFIGURATION_PROXY_CONF_PATH, "src/test/resources/conf-proxy-conf");
        System.setProperty(CONFIGURATION_PROXY_GENERATED_CONF_PATH, "build/tmp/test/generated-conf");
        System.setProperty(CONFIGURATION_PATH, "src/test/resources/test-conf-simple");
        System.setProperty(TEMP_FILES_PATH, "build/tmp/test");
    }

    @Test
    public void cleanupTempDirectoriesWhenBuildingSignedDirectoryFails() throws Exception {
        ConfProxyProperties conf = new ConfProxyProperties("PROXY1");
        ConfProxyHelper.purgeOutdatedGenerations(conf);
        VersionedConfigurationDirectory confDir = new VersionedConfigurationDirectory(conf.getConfigurationDownloadPath(2));

        try (MockedStatic<SignerProxy> signerProxyMock = mockStatic(SignerProxy.class)) {
            signerProxyMock.when(() -> SignerProxy.getSignMechanism(any()))
                    .thenThrow(new CodedException("InternalError", "Signer is unreachable"));
            try (OutputBuilder output = new OutputBuilder(confDir, conf, 2)) {
                CodedException exception = assertThrows(CodedException.class, output::buildSignedDirectory);
                assertEquals("InternalError: Signer is unreachable", exception.getMessage());
            }
            assertEquals(0, Files.list(Paths.get("build/tmp/test/PROXY1")).count());
        }
    }

}
