/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.CertificateEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.TestCertUtil;

import static ee.ria.xroad.common.ErrorCodes.X_MALFORMED_GLOBALCONF;
import static ee.ria.xroad.common.conf.globalconf.PrivateParameters.CONTENT_ID_PRIVATE_PARAMETERS;
import static ee.ria.xroad.common.conf.globalconf.SharedParameters.CONTENT_ID_SHARED_PARAMETERS;
import static org.junit.Assert.*;

/**
 * Tests to verify configuration downloading procedure.
 */
public class ConfigurationClientTest {

    /**
     * Test to ensure a simple configuration will be downloaded.
     * @throws Exception in case of any errors
     */
    @Test
    public void downloadSimpleConf() throws Exception {
        String confPath = "src/test/resources/test-conf-simple";

        List<String> receivedParts = new ArrayList<>();
        List<String> deletedFiles = new ArrayList<>();

        ConfigurationClient client =
                getClient(confPath, receivedParts, deletedFiles);
        client.execute();

        assertEquals(3, receivedParts.size());
        assertTrue(receivedParts.contains(CONTENT_ID_PRIVATE_PARAMETERS));
        assertTrue(receivedParts.contains(CONTENT_ID_SHARED_PARAMETERS));
        assertTrue(receivedParts.contains("FOO"));

        assertEquals(1, deletedFiles.size());
        assertTrue(deletedFiles.contains("bar.xml"));
    }

    /**
     * Test to ensure a detached configuration will be downloaded.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void downloadDetachedConf() throws Exception {
        String confPath = "src/test/resources/test-conf-detached";

        List<String> receivedParts = new ArrayList<>();
        List<String> deletedFiles = new ArrayList<>();

        ConfigurationClient client =
                getClient(confPath, receivedParts, deletedFiles);
        client.execute();

        assertEquals(2, receivedParts.size());
        assertTrue(receivedParts.contains(CONTENT_ID_PRIVATE_PARAMETERS));
        assertTrue(receivedParts.contains(CONTENT_ID_SHARED_PARAMETERS));

        assertEquals(0, deletedFiles.size());
    }

    /**
     * Test to ensure a malformed configuration will not be downloaded.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void downloadConfFail() throws Exception {
        String confPath = "src/test/resources/test-conf-malformed";

        List<String> receivedParts = new ArrayList<>();
        List<String> deletedFiles = new ArrayList<>();

        ConfigurationClient client =
                getClient(confPath, receivedParts, deletedFiles);
        try {
            client.execute();
            fail("Should fail to download");
        } catch (CodedException expected) {
            assertEquals(X_MALFORMED_GLOBALCONF, expected.getFaultCode());
        }

        assertEquals(0, deletedFiles.size());
    }

    // ------------------------------------------------------------------------

    private static ConfigurationAnchor getConfigurationAnchor(
            final String fileName) {
        return new ConfigurationAnchor((String) null) {

            @Override
            public boolean hasChanged() {
                return false;
            }

            @Override
            public List<ConfigurationLocation> getLocations() {
                try {
                    return Arrays.asList(new ConfigurationLocation(this,
                            fileName, Arrays.asList(
                                    TestCertUtil.getConsumer().cert
                                        .getEncoded())));
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

    private static ConfigurationClient getClient(final String confPath,
            final List<String> receivedParts, final List<String> deletedFiles) {
        ConfigurationAnchor configurationAnchor =
                getConfigurationAnchor(confPath + ".txt");

        FileNameProvider fileNameProvider = new FileNameProviderImpl(confPath);

        ConfigurationDownloader configurations =
                new ConfigurationDownloader(fileNameProvider) {
            @Override
            ConfigurationParser getParser() {
                return new ConfigurationParser(instanceIdentifiers) {
                    @Override
                    protected InputStream getInputStream() throws Exception {
                        String downloadURL =
                                configuration.getLocation().getDownloadURL();
                        return new FileInputStream(downloadURL);
                    }
                };
            }

            @Override
            boolean shouldDownload(ConfigurationFile file, Path p)
                    throws Exception {
                return true;
            }

            @Override
            void persistContent(byte[] content, Path destination,
                    ConfigurationFile file) throws Exception {
                receivedParts.add(file.getContentIdentifier());
            }

            @Override
            void updateExpirationDate(Path destination, ConfigurationFile file)
                    throws Exception {
            }

            @Override
            byte[] downloadContent(ConfigurationLocation location,
                    ConfigurationFile file) throws Exception {
                try (InputStream in = Files.newInputStream(
                        Paths.get(confPath, file.getInstanceIdentifier(),
                                file.getContentLocation()))) {
                    return IOUtils.toByteArray(in);
                }
            }
        };

        DownloadedFiles downloadedFiles = new DownloadedFiles(
                Paths.get(confPath, "files")) {
            @Override
            void delete(String file) {
                deletedFiles.add(file);
            }

            @Override
            void save() throws Exception {
            }
        };

        return new ConfigurationClient(downloadedFiles, configurations,
                configurationAnchor);
    }
}
