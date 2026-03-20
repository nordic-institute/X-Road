/*
 * The MIT License
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.confproxy.common.service;

import ee.ria.xroad.common.crypto.Digests;
import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;
import ee.ria.xroad.common.util.AtomicSave;
import ee.ria.xroad.common.util.CryptoUtils;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.confproxy.common.config.ConfigurationProxyProperties;
import org.niis.xroad.confproxy.common.domain.ConfProxyInstance;
import org.niis.xroad.globalconf.model.ConfigurationAnchor;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import static org.niis.xroad.common.core.exception.ErrorCode.NOT_FOUND;
import static org.niis.xroad.confproxy.common.domain.ConfProxyInstance.ACTIVE_SIGNING_KEY_ID;
import static org.niis.xroad.confproxy.common.domain.ConfProxyInstance.ANCHOR_XML;
import static org.niis.xroad.confproxy.common.domain.ConfProxyInstance.SIGNING_KEY_ID_PREFIX;
import static org.niis.xroad.confproxy.common.domain.ConfProxyInstance.VALIDITY_INTERVAL_SECONDS;
import static org.niis.xroad.confproxy.common.exceptions.ConfProxyErrorCode.ANCHOR_LOAD_ERROR;
import static org.niis.xroad.confproxy.common.exceptions.ConfProxyErrorCode.EXISTING_INSTANCE_ERROR;
import static org.niis.xroad.confproxy.common.exceptions.ConfProxyErrorCode.LOAD_INSTANCE_ERROR;
import static org.niis.xroad.confproxy.common.exceptions.ConfProxyErrorCode.SAVE_INSTANCE_ERROR;
import static org.niis.xroad.confproxy.common.utils.ConfProxyUtils.subDirectoryNames;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class ConfProxyInstanceService {
    public static final String CONF_INI = "conf.ini";

    private final ConfigurationProxyProperties cpProperties;

    /**
     * Gets all existing subdirectory names from the configuration proxy
     * configuration directory, which correspond to the configuration proxy
     * instance ids.
     * @return list of configuration proxy instance ids
     */
    public List<String> availableInstancesNames() {
        Path confPath = Paths.get(cpProperties.configurationPath());

        return subDirectoryNames(confPath);
    }

    /**
     * Generates a string that describes the certificate information for the
     * provided key id.
     * @param keyId the key id
     * @param conf  configuration proxy properties instance
     * @return string describing certificate information
     */
    public Optional<CertInfo> certInfo(final ConfProxyInstance conf, final String keyId) {
        if (keyId == null) {
            return Optional.empty();
        }
        Path certPath = conf.getCertPath(keyId).toAbsolutePath();
        byte[] certBytes;
        try {
            certBytes = Files.readAllBytes(certPath);
        } catch (IOException e) {
            log.warn("Cert file missing: {}", certPath, e);
            return Optional.of(new CertInfo(CertInfo.State.MISSING, null));
        }
        try {
            CryptoUtils.readCertificate(certBytes);
        } catch (Exception e) {
            log.warn("Invalid certificate: {}", certPath, e);
            return Optional.of(new CertInfo(CertInfo.State.INVALID, e.getMessage()));
        }

        return Optional.of(new CertInfo(CertInfo.State.OK, certPath.toString()));
    }

    /**
     * Generates a colon delimited hex string describing the anchor file for
     * the given proxy instance.
     * @param conf configuration proxy properties instance
     * @return colon delimited hex string describing the anchor file
     */
    public AnchorAndHash anchorHash(final ConfProxyInstance conf) {
        Path anchorPath = Paths.get(conf.getProxyAnchorPath());
        if (!Files.exists(anchorPath)) {
            throw XrdRuntimeException.systemException(ANCHOR_LOAD_ERROR)
                    .details("'" + ANCHOR_XML + "' don't exist")
                    .build();
        }

        ConfigurationAnchor anchor;

        try {
            anchor = new ConfigurationAnchor(conf.getProxyAnchorPath());
        } catch (Exception e) {
            throw XrdRuntimeException.systemException(ANCHOR_LOAD_ERROR)
                    .details("'" + ANCHOR_XML + "' could not be loaded: " + e.getMessage())
                    .cause(e)
                    .build();
        }

        byte[] anchorBytes;
        try {
            anchorBytes = Files.readAllBytes(anchorPath);
        } catch (IOException e) {
            throw XrdRuntimeException.systemException(ANCHOR_LOAD_ERROR)
                    .details("'" + ANCHOR_XML + "' could not be loaded: " + e.getMessage())
                    .cause(e)
                    .build();
        }

        try {
            String hash = Digests.hexDigest(DigestAlgorithm.SHA224, anchorBytes);
            return new AnchorAndHash(anchor, StringUtils.join(hash.toUpperCase().split("(?<=\\G.{2})"), ':'));
        } catch (IOException e) {
            throw XrdRuntimeException.systemException(ANCHOR_LOAD_ERROR)
                    .details("Failed calculate digest for anchor")
                    .cause(e)
                    .build();
        }
    }

    public ConfProxyInstance newInstance(String instance) {
        var configBasePath = Paths.get(cpProperties.configurationPath(), instance);
        var configFile = configBasePath.resolve(CONF_INI);
        try {
            Files.createDirectories(configBasePath);
            Files.createFile(configFile);
        } catch (FileAlreadyExistsException e) {
            throw XrdRuntimeException.systemException(EXISTING_INSTANCE_ERROR)
                    .details("Configuration for instance '%s' already exists".formatted(instance))
                    .cause(e)
                    .build();
        } catch (IOException e) {
            throw XrdRuntimeException.systemException(SAVE_INSTANCE_ERROR)
                    .details("Cannot create config file: %s".formatted(configFile))
                    .cause(e)
                    .build();
        }

        return load(instance, configFile);
    }

    public boolean exists(String instance) {
        var configFile = Paths.get(cpProperties.configurationPath(), instance, CONF_INI);

        return Files.exists(configFile);
    }

    public ConfProxyInstance loadInstance(String instance) {
        var configFile = Paths.get(cpProperties.configurationPath(), instance, CONF_INI);

        if (!exists(instance)) {
            throw XrdRuntimeException.systemException(NOT_FOUND)
                    .details("Instance '%s' don't exist".formatted(instance))
                    .build();
        }

        return load(instance, configFile);
    }


    public void addSigningKey(final ConfProxyInstance instance, final String keyId, final byte[] certBytes) {
        saveCert(instance, keyId, certBytes);
        addKeyId(instance, keyId);
        if (instance.getActiveSigningKey() == null) {
            setActiveSigningKey(instance, keyId);
        }
    }

    /**
     * Removes the given key id from the configuration.
     * @param keyId the id to be removed
     * @return true if the given key id was found and removed
     */
    public boolean removeKeyId(final ConfProxyInstance instance, final String keyId) {
        var config = instance.getConfig();
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
            deleteCert(instance, keyId);
            saveInstance(instance);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Configures the active signing key id.
     * @param keyId new active signing key id
     */
    public boolean setActiveSigningKey(final ConfProxyInstance instance, final String keyId) {
        if (instance.getKeyList().contains(keyId)) {
            instance.getConfig().setProperty(ACTIVE_SIGNING_KEY_ID, keyId);
            saveInstance(instance);
            return true;
        }
        return false;
    }

    /**
     * Configures the validity interval of the generated configurations.
     * @param value number of seconds the configurations should be valid
     */
    public void setValidityIntervalSeconds(ConfProxyInstance instance, final int value) {
        instance.getConfig().setProperty(VALIDITY_INTERVAL_SECONDS, value);
        saveInstance(instance);
    }

    /**
     * Deletes the certificate file for the given key id.
     * @param keyId the id for the key corresponding to the certificate
     */
    private void deleteCert(ConfProxyInstance instance, final String keyId) {
        try {
            Files.delete(instance.getCertPath(keyId));
        } catch (IOException e) {
            throw XrdRuntimeException.systemException(SAVE_INSTANCE_ERROR)
                    .details("Failed to delete cert for key id: '%s'".formatted(keyId))
                    .cause(e)
                    .build();
        }
    }

    /**
     * Saves the given certificate to the appropriate location.
     * @param keyId     the key id the certificate corresponds to
     * @param certBytes the byte contents of the certificate
     */
    private void saveCert(final ConfProxyInstance instance, final String keyId, final byte[] certBytes) {
        String filePath = instance.getCertPath(keyId).toString();
        try {
            AtomicSave.execute(filePath, "tmpcert", out -> CryptoUtils.writeCertificatePem(certBytes, out));
        } catch (Exception e) {
            throw XrdRuntimeException.systemException(SAVE_INSTANCE_ERROR)
                    .details("Failed to save cert: " + filePath)
                    .cause(e)
                    .build();
        }
    }

    /**
     * Adds the given key id to the configuration.
     * @param keyId the id to be added
     */
    private void addKeyId(final ConfProxyInstance instance, final String keyId) {
        int nextKeyNumber = getNextKeyNumber(instance);
        instance.getConfig().addProperty(SIGNING_KEY_ID_PREFIX + nextKeyNumber, keyId);
        saveInstance(instance);
    }

    /**
     * Get next available key number.
     * @return the next available key number
     */
    private int getNextKeyNumber(final ConfProxyInstance instance) {
        int n = 1;
        while (instance.getConfig().containsKey(SIGNING_KEY_ID_PREFIX + n)) {
            ++n;
        }
        return n;
    }

    private ConfProxyInstance load(String instance, Path configFile) {
        try (var reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            var config = new INIConfiguration();
            config.read(reader);

            return new ConfProxyInstance(
                    instance,
                    cpProperties.configurationPath(),
                    cpProperties.generatedConfPath(),
                    config);
        } catch (IOException | ConfigurationException e) {
            log.error("Failed to load '{}': {}", configFile, e.getMessage());
            throw XrdRuntimeException.systemException(LOAD_INSTANCE_ERROR)
                    .details("Failed to load configuration for '%s'".formatted(instance))
                    .cause(e)
                    .build();
        }
    }

    private void saveInstance(ConfProxyInstance instance) {
        var configFile = Paths.get(cpProperties.configurationPath(), instance.getInstance(), CONF_INI);
        try (Writer writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
            instance.getConfig().write(writer);
        } catch (IOException | ConfigurationException e) {
            throw XrdRuntimeException.systemException(SAVE_INSTANCE_ERROR)
                    .details("Failed to save configuration for '%s'".formatted(instance.getInstance()))
                    .cause(e)
                    .build();
        }
    }

    public record AnchorAndHash(ConfigurationAnchor anchor, String hash) {
    }

    public record CertInfo(State state, String info) {
        public enum State {
            MISSING, INVALID, OK
        }

        @Override
        public @NonNull String toString() {
            return switch (state) {
                case MISSING -> "(CERTIFICATE FILE MISSING!)";
                case INVALID -> "(INVALID CERTIFICATE - " + info + ")";
                case OK -> "(Certificate: " + info + ")";
            };
        }
    }
}
