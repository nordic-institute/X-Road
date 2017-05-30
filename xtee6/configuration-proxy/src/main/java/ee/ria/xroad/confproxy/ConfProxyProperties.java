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
package ee.ria.xroad.confproxy;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.util.AtomicSave;
import ee.ria.xroad.common.util.CryptoUtils;

/**
 * Defines the set of properties for a configuration proxy instance and provides
 * means to of altering these properties.
 */
@Slf4j
public class ConfProxyProperties {

    public static final String ACTIVE_SIGNING_KEY_ID =
            "active-signing-key-id";

    public static final String SIGNING_KEY_ID_PREFIX =
            "signing-key-id-";

    public static final String VALIDITY_INTERVAL_SECONDS =
            "validity-interval-seconds";

    public static final String CONF_INI = "conf.ini";
    public static final String ANCHOR_XML = "anchor.xml";

    private static final String CERT_EXTENSION = ".pem";

    private HierarchicalINIConfiguration config;

    @Getter
    String instance;

    /**
     * Constructs the configuration for the given
     * configuration proxy instance id.
     * @param name the if of the configuration proxy instance
     * @throws ConfigurationException if the configuration could not be loaded
     */
    public ConfProxyProperties(final String name)
            throws ConfigurationException {
        this.instance = name;
        String confDir = SystemProperties.getConfigurationProxyConfPath();
        File configFile = Paths.get(confDir, instance, CONF_INI).toFile();
        if (!configFile.exists()) {
            throw new ConfigurationException("'" + CONF_INI
                    + "' does not exist.");
        }
        try {
            config = new HierarchicalINIConfiguration(configFile);
        } catch (ConfigurationException e) {
            log.error("Failed to load '{}': {}", configFile, e.getMessage());
            throw e;
        }
    }

    /**
     * Gets the location of the configuration client script, which downloads
     * the global configuration.
     * @return path to the configuration client script
     */
    public static String getDownloadScriptPath() {
        return SystemProperties.getConfigurationProxyDownloadScript();
    }

    /**
     * Gets the path to the directory which should hold the downloaded global
     * configuration files for this configuration proxy instance.
     * @return download path for the global configuration files
     */
    public final String getConfigurationDownloadPath() {
        return Paths.get(SystemProperties.getConfigurationPath(), instance)
                .toString();
    }

    /**
     * Gets the path to the directory which should hold the generated global
     * configuration files for this configuration proxy instance.
     * @return path to the global configuration destination
     */
    public final String getConfigurationTargetPath() {
        return Paths.get(
                SystemProperties.getConfigurationProxyGeneratedConfPath(),
                instance).toString();
    }

    /**
     * Gets the configured temporary directory path for the generated global
     * configuration files.
     * @return path to the temporary directory
     */
    public final String getTemporaryDirectoryPath() {
        return Paths.get(SystemProperties.getTempFilesPath(),
                instance).toString();
    }

    /**
     * Gets the default path for the configuration proxy
     * instance 'anchor.xml' file.
     * @return the configuration proxy instance 'anchor.xml' file.
     */
    public final String getProxyAnchorPath() {
        return Paths.get(SystemProperties.getConfigurationProxyConfPath(),
                instance, ANCHOR_XML).toString();
    }

    /**
     * Gets the public URL where configurations should be available,
     * 'configuration-proxy.address' needs to be defined in 'local.ini'.
     * @return the URL where global configurations are made available
     * @throws Exception if the configured configuration proxy
     * address is invalid
     */
    public final String getConfigurationProxyURL() throws Exception {
        String address = SystemProperties.getConfigurationProxyAddress();
        URI uri = new URI("http", address, "/" + instance, null);
        return uri.toString();
    }

    /**
     * Gets the configured active signing key id.
     * @return the configured active signing key id
     */
    public final String getActiveSigningKey() {
        if (activeSigningKeyCount() > 1) {
            log.warn("Multiple active signing keys configured!");
        }
        return config.getString(ACTIVE_SIGNING_KEY_ID);
    }

    /**
     * Configures the active signing key id.
     * @param keyId new active signing key id
     * @throws ConfigurationException
     * if an error occurs when saving the configuration
     */
    public final void setActiveSigningKey(final String keyId)
            throws ConfigurationException {
        config.setProperty(ACTIVE_SIGNING_KEY_ID, keyId);
        config.save();
    }

    /**
     * Gets the id for the configured signature algorithm.
     * @return the id of the configured signature algorithm.
     */
    public final String getSignatureAlgorithmId() {
        return SystemProperties.getConfigurationProxySignatureAlgorithmId();
    }

    /**
     * Gets the configured validity interval.
     * @return the configured validity interval in seconds
     */
    public final int getValidityIntervalSeconds() {
        return config.getInteger(VALIDITY_INTERVAL_SECONDS, -1);
    }

    /**
     * Configures the validity interval of the generated configurations.
     * @param value number of seconds the configurations should be valid
     * @throws ConfigurationException
     * if an error occurs when saving the configuration
     */
    public final void setValidityIntervalSeconds(final int value)
            throws ConfigurationException {
        config.setProperty(VALIDITY_INTERVAL_SECONDS, value);
        config.save();
    }

    /**
     * Gets the URI for the configured hash algorithm.
     * @return the URI of the configured hash algorithm.
     */
    public final String getHashAlgorithmURI() {
        return SystemProperties.getConfigurationProxyHashAlgorithmUri();
    }

    /**
     * Reads all certificate bytes from disk.
     * @return a list of certificate byte content
     * @throws IOException if reading a certificate from disk failed
     */
    public final List<byte[]> getVerificationCerts() throws IOException {
        List<X509Certificate> certs = new ArrayList<>();
        if (getActiveSigningKey() != null) {
            certs.add(readCert(getActiveSigningKey()));
        }
        getKeyList().forEach(k -> certs.add(readCert(k)));
        return certs.stream().distinct().map(ConfProxyProperties::certBytes)
                .collect(Collectors.toList());
    }

    /**
     * Reads configured keys from the configuration.
     * @return a list containing configured key ids
     */
    public final List<String> getKeyList() {
        List<String> keys = new ArrayList<String>();
        Iterator<String> signingKeys = config.getKeys();
        while (signingKeys.hasNext()) {
            String k = signingKeys.next();
            if (k.startsWith(SIGNING_KEY_ID_PREFIX)) {
                String keyId = config.getString(k);
                keys.add(keyId);
            }
        }
        return keys;
    }

    /**
     * Saves the given certificate to the appropriate location.
     * @param keyId the key id the certificate corresponds to
     * @param certBytes the byte contents of the certificate
     * @throws Exception if an error occurs when saving the certificate to disk
     */
    public final void saveCert(final String keyId, final byte[] certBytes)
            throws Exception {
        String filePath = getCertPath(keyId).toString();
        AtomicSave.execute(filePath, "tmpcert",
                out -> CryptoUtils.writeCertificatePem(certBytes, out));
    }

    /**
     * Adds the given key id to the configuration.
     * @param keyId the id to be added
     * @throws ConfigurationException
     * if an error occurs when saving the configuration
     */
    public final void addKeyId(final String keyId)
            throws ConfigurationException {
        int nextKeyNumber = getNextKeyNumber();
        config.addProperty(SIGNING_KEY_ID_PREFIX + nextKeyNumber, keyId);
        config.save();
    }

    /**
     * Removes the given key id from the configuration.
     * @param keyId the id to be removed
     * @return true if the given key id was found and removed
     * @throws ConfigurationException
     * if an error occurs when saving the configuration
     */
    public final boolean removeKeyId(final String keyId)
            throws ConfigurationException {
        Iterator<String> signingKeys = config.getKeys();
        String keyIdProperty = null;
        while (signingKeys.hasNext()) {
            String k = signingKeys.next();
            if (config.getProperty(k).equals(keyId)) {
                keyIdProperty = k;
                break;
            }
        }
        if (keyIdProperty != null) {
            config.clearProperty(keyIdProperty);
            config.save();
            return true;
        } else {
            return false;
        }
    }

    /**
     * Checks whether the configuration is loaded correctly.
     * @return true if the configuration file has been loaded.
     */
    public final boolean exists() {
        return config != null;
    }

    /**
     * Deletes the certificate file for the given key id.
     * @param keyId the id for the key corresponding to the certificate
     * @throws IOException if an I/O error occurs
     */
    public final void deleteCert(final String keyId) throws IOException {
        Files.delete(getCertPath(keyId));
    }

    /**
     * Constructs the path to the certificate file for the given key id.
     * @param keyId the id for the key corresponding to the certificate
     * @return the path to the certificate file
     */
    public final Path getCertPath(final String keyId) {
        return Paths.get(SystemProperties.getConfigurationProxyConfPath(),
                instance, "cert_" + keyId + CERT_EXTENSION);
    }

    /**
     * Get the current active signing key count.
     * @return the active signing key count
     */
    private int activeSigningKeyCount() {
        Object activeKeyProperty = config.getProperty(ACTIVE_SIGNING_KEY_ID);
        if (activeKeyProperty instanceof ArrayList) {
            return ((ArrayList<?>) activeKeyProperty).size();
        }
        return activeKeyProperty != null ? 1 : 0;
    }

    /**
     * Get next available key number.
     * @return the next available key number
     */
    private int getNextKeyNumber() {
        int n = 1;
        for (; config.containsKey(SIGNING_KEY_ID_PREFIX + n); ++n) {
            continue;
        }
        return n;
    }

    /**
     * Read the certificate for the provided key id from disk.
     * @param keyId the key id
     * @return the certificate for the provided key id
     */
    private X509Certificate readCert(final String keyId) {
        try (InputStream is =
                new FileInputStream(getCertPath(keyId).toFile())) {
            return CryptoUtils.readCertificate(is);
        } catch (Exception e) {
            log.error("Failed to read cert for key ID '{}'. Exception: {}", keyId, e);
            return null;
        }
    }

    /**
     * Quietly get the raw bytes of a certificate.
     * @param cert the certificate
     * @return raw bytes for the provided certificate
     */
    @SneakyThrows
    private static byte[] certBytes(final X509Certificate cert) {
        return cert.getEncoded();
    }
}
