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

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.TestCertUtil;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.niis.xroad.globalconf.model.ConfigurationAnchor;
import org.niis.xroad.globalconf.model.ConfigurationDirectory;
import org.niis.xroad.globalconf.model.ConfigurationLocation;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.CertificateEncodingException;
import java.util.ArrayList;
import java.util.List;

import static ee.ria.xroad.common.ErrorCodes.X_MALFORMED_GLOBALCONF;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.niis.xroad.globalconf.model.ConfigurationConstants.CONTENT_ID_PRIVATE_PARAMETERS;
import static org.niis.xroad.globalconf.model.ConfigurationConstants.CONTENT_ID_SHARED_PARAMETERS;

/**
 * Tests to verify configuration downloading procedure.
 */
@Slf4j
class ConfigurationClientTest {

    @TempDir
    File tempDir;

    /**
     * Test to ensure a simple configuration will be downloaded.
     *
     * @throws Exception in case of any errors
     */
    @Test
    void downloadSimpleConf() throws Exception {
        String confPath = "src/test/resources/test-conf-simple";

        List<String> receivedParts = new ArrayList<>();

        initInstanceIdentifier();

        ConfigurationClient client = getClient(confPath, receivedParts);
        client.execute();

        assertEquals(3, receivedParts.size());
        assertTrue(receivedParts.contains(CONTENT_ID_PRIVATE_PARAMETERS));
        assertTrue(receivedParts.contains(CONTENT_ID_SHARED_PARAMETERS));
        assertTrue(receivedParts.contains("FOO"));
    }

    /**
     * Test to ensure a detached configuration will be downloaded.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test
    void downloadDetachedConf() throws Exception {
        String confPath = "src/test/resources/test-conf-detached";

        List<String> receivedParts = new ArrayList<>();

        initInstanceIdentifier();

        ConfigurationClient client = getClient(confPath, receivedParts);
        client.execute();

        assertEquals(2, receivedParts.size());
        assertTrue(receivedParts.contains(CONTENT_ID_PRIVATE_PARAMETERS));
        assertTrue(receivedParts.contains(CONTENT_ID_SHARED_PARAMETERS));

    }

    /**
     * Test to ensure a malformed configuration will not be downloaded.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test
    void downloadConfFail() throws Exception {
        String confPath = "src/test/resources/test-conf-malformed";

        List<String> receivedParts = new ArrayList<>();

        initInstanceIdentifier();

        ConfigurationClient client = getClient(confPath, receivedParts);

        try {
            client.execute();

            fail("Should fail to download");
        } catch (CodedException expected) {
            assertEquals(X_MALFORMED_GLOBALCONF, expected.getFaultCode());
        }
    }

    // ------------------------------------------------------------------------

    private static ConfigurationAnchor getConfigurationAnchor(final String fileName) {
        return new ConfigurationAnchor((String) null) {

            @Override
            public boolean hasChanged() {
                return false;
            }

            @Override
            public List<ConfigurationLocation> getLocations() {
                try {
                    return List.of(new ConfigurationLocation(this.getInstanceIdentifier(),
                            fileName,
                            List.of(TestCertUtil.getConsumer().certChain[0].getEncoded())));
                } catch (CertificateEncodingException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public String getInstanceIdentifier() {
                return "EE";
            }
        };
    }

    private void initInstanceIdentifier() throws Exception {
        ConfigurationDirectory.saveInstanceIdentifier(tempDir.getAbsolutePath(), "EE");
    }

    private ConfigurationClient getClient(final String confPath, final List<String> receivedParts) {
        ConfigurationAnchor configurationAnchor = getConfigurationAnchor(confPath + ".txt");

        ConfigurationDownloader configurations = new ConfigurationDownloader(
                tempDir.getAbsolutePath(), 2) {
            @Override
            ConfigurationParser getParser() {
                return new ConfigurationParser(mock(ConfigurationDownloader.class)) {
                    @Override
                    protected InputStream getInputStream() throws Exception {
                        String downloadURL = configuration.getLocation().getDownloadURL();
                        // Because the test case cannot handle query parameters
                        // we need to strip them from download URL.
                        int idx = downloadURL.lastIndexOf("?");

                        if (idx != -1) {
                            downloadURL = downloadURL.substring(0, downloadURL.lastIndexOf("?"));
                        }

                        return new FileInputStream(downloadURL);
                    }
                };
            }

            @Override
            boolean shouldDownload(ConfigurationFile file, Path p) throws Exception {
                return true;
            }

            @Override
            void persistContent(byte[] content, Path destination, ConfigurationFile file) throws Exception {
                receivedParts.add(file.getContentIdentifier());
                super.persistContent(content, destination, file);
            }

            @Override
            void updateExpirationDate(Path destination, ConfigurationFile file) throws Exception {
            }

            @Override
            byte[] downloadContent(ConfigurationLocation location, ConfigurationFile file) throws Exception {
                try (InputStream in = Files.newInputStream(
                        Paths.get(confPath, file.getInstanceIdentifier(), file.getContentLocation()))) {
                    return IOUtils.toByteArray(in);
                }
            }
        };

        return new ConfigurationClient(tempDir.getAbsolutePath(), configurations, configurationAnchor);
    }
}
