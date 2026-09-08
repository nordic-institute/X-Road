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
package org.niis.xroad.confproxy.common.domain;

import ee.ria.xroad.common.util.CryptoUtils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.configuration2.INIConfiguration;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class ConfProxyInstance {

    public static final String ACTIVE_SIGNING_KEY_ID = "active-signing-key-id";
    public static final String VALIDITY_INTERVAL_SECONDS = "validity-interval-seconds";
    public static final String SIGNING_KEY_ID_PREFIX = "signing-key-id-";
    public static final String ANCHOR_XML = "anchor.xml";
    private static final String CERT_EXTENSION = ".pem";


    @Getter
    private final String instance;
    private final String configurationPath;
    private final String generatedConfPath;
    @Getter
    private final INIConfiguration config;

    public String getInstanceConfigurationPath() {
        return Paths.get(configurationPath, instance).toString();
    }

    /**
     * Gets the path to the directory which should hold the generated global
     * configuration files for this configuration proxy instance.
     * @return path to the global configuration destination
     */
    public final String getConfigurationTargetPath() {
        return Paths.get(generatedConfPath, instance).toString();
    }

    /**
     * Gets the configured validity interval.
     * @return the configured validity interval in seconds
     */
    public final int getValidityIntervalSeconds() {
        return config.getInteger(VALIDITY_INTERVAL_SECONDS, -1);
    }

    /**
     * Gets the default path for the configuration proxy
     * instance 'anchor.xml' file.
     * @return the configuration proxy instance 'anchor.xml' file.
     */
    public final String getProxyAnchorPath() {
        return Paths.get(getInstanceConfigurationPath(), ANCHOR_XML).toString();
    }


    /**
     * Reads configured keys from the configuration.
     * @return a list containing configured key ids
     */
    public final List<String> getKeyList() {
        List<String> keys = new ArrayList<>();
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
     * Reads all certificate bytes from disk.
     * @return a list of certificate byte content
     */
    public final List<byte[]> getVerificationCerts() {
        List<X509Certificate> certs = new ArrayList<>();
        if (getActiveSigningKey() != null) {
            certs.add(readCert(getActiveSigningKey()));
        }
        getKeyList().forEach(k -> certs.add(readCert(k)));
        return certs.stream().distinct().map(ConfProxyInstance::certBytes)
                .collect(Collectors.toList());
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
     * Constructs the path to the certificate file for the given key id.
     * @param keyId the id for the key corresponding to the certificate
     * @return the path to the certificate file
     */
    public final Path getCertPath(final String keyId) {
        return Paths.get(getInstanceConfigurationPath(), "cert_" + keyId + CERT_EXTENSION);
    }

    public final boolean isReady() {
        return activeSigningKeyCount() > 0 && Files.exists(Path.of(getProxyAnchorPath()));
    }

    /**
     * Read the certificate for the provided key id from disk.
     * @param keyId the key id
     * @return the certificate for the provided key id
     */
    private X509Certificate readCert(final String keyId) {
        try (InputStream is = new FileInputStream(getCertPath(keyId).toFile())) {
            return CryptoUtils.readCertificate(is);
        } catch (Exception e) {
            log.error("Failed to read cert for key ID '{}'", keyId, e);

            return null;
        }
    }

    /**
     * Quietly get the raw bytes of a certificate.
     * @param cert the certificate
     * @return raw bytes for the provided certificate
     */
    private static byte[] certBytes(final X509Certificate cert) {
        try {
            return cert.getEncoded();
        } catch (CertificateEncodingException e) {
            throw XrdRuntimeException.systemException(e);
        }
    }
}
