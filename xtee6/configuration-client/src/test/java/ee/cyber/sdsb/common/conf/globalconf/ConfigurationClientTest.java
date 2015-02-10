package ee.cyber.sdsb.common.conf.globalconf;

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

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.TestCertUtil;

import static ee.cyber.sdsb.common.ErrorCodes.X_MALFORMED_GLOBALCONF;
import static ee.cyber.sdsb.common.conf.globalconf.PrivateParameters.CONTENT_ID_PRIVATE_PARAMETERS;
import static ee.cyber.sdsb.common.conf.globalconf.SharedParameters.CONTENT_ID_SHARED_PARAMETERS;
import static org.junit.Assert.*;

public class ConfigurationClientTest {

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
