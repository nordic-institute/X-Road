/*
 * The MIT License
 *
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
package org.niis.xroad.common.vault;

import ee.ria.xroad.common.conf.InternalSSLKey;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.properties.NodeProperties;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.util.stream.Stream;

import static java.util.Arrays.stream;
import static org.niis.xroad.common.core.exception.ErrorCode.MISSING_SECRET;

/**
 * Single path for reading TLS material from the secret store and provisioning it when absent.
 * <p>
 * {@link #readOrProvision} provisions the material whenever the store reports it absent. It is for a service
 * that runs as a single instance, and for material that belongs to one Central Server node and is stored under
 * that node's own path. Central Server nodes have no primary or secondary role.
 * <p>
 * {@link #readOrProvisionOnPrimary} is for material shared by every node of a Security Server cluster: a
 * primary or standalone node provisions it, while a secondary node never creates or replaces anything and
 * instead reports the absent key to its caller, whose service restart retries the read once the primary
 * has provisioned it.
 * <p>
 * In all cases, any read failure other than the store reporting the key absent, such as the store being
 * unreachable, sealed or failing over, is passed on: it never leads to a key being created or replaced.
 */
@Slf4j
@UtilityClass
public class TlsKeyProvisioning {

    @FunctionalInterface
    public interface KeyReader {
        InternalSSLKey read() throws IOException, GeneralSecurityException;
    }

    @FunctionalInterface
    public interface KeyWriter {
        void write(InternalSSLKey key) throws IOException, GeneralSecurityException;
    }

    /**
     * @param name      the key name, used in log messages
     * @param reader    reads the existing key; reports an absent key with {@code MISSING_SECRET}
     * @param writer    stores a newly provisioned key
     * @param keyClient provisions a new key and certificate chain
     * @return the existing key, or the newly provisioned and stored one
     */
    public static InternalSSLKey readOrProvision(String name, KeyReader reader, KeyWriter writer, VaultKeyClient keyClient)
            throws IOException, GeneralSecurityException {
        try {
            return reader.read();
        } catch (XrdRuntimeException e) {
            if (!e.isCausedBy(MISSING_SECRET)) {
                throw e;
            }
            log.warn("Unable to locate '{}' TLS credentials, attempting to create new ones", name);
        }
        return provisionAndStore(name, writer, keyClient);
    }

    /**
     * @param name      the key name, used in log messages
     * @param reader    reads the existing key; reports an absent key with {@code MISSING_SECRET}
     * @param writer    stores a newly provisioned key; never called on a secondary node
     * @param keyClient provisions a new key and certificate chain; never called on a secondary node
     * @return the existing key, or the newly provisioned and stored one
     */
    public static InternalSSLKey readOrProvisionOnPrimary(String name, KeyReader reader, KeyWriter writer,
                                                          VaultKeyClient keyClient)
            throws IOException, GeneralSecurityException {
        try {
            return reader.read();
        } catch (XrdRuntimeException e) {
            if (!e.isCausedBy(MISSING_SECRET)) {
                throw e;
            }
            if (NodeProperties.isSecondaryNode()) {
                log.warn("'{}' TLS credentials not found, the primary node creates them", name);
                throw e;
            }
            log.warn("Unable to locate '{}' TLS credentials, attempting to create new ones", name);
        }
        return provisionAndStore(name, writer, keyClient);
    }

    private static InternalSSLKey provisionAndStore(String name, KeyWriter writer, VaultKeyClient keyClient)
            throws IOException, GeneralSecurityException {
        var tlsKey = provision(keyClient);
        writer.write(tlsKey);
        log.info("Successfully created '{}' TLS credentials", name);
        return tlsKey;
    }

    /**
     * @param keyClient provisions a new key and certificate chain
     * @return the new key with its identity certificate chain followed by the trust certificates
     */
    public static InternalSSLKey provision(VaultKeyClient keyClient)
            throws CertificateException, IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        var keyData = keyClient.provisionNewCerts();
        var certChain = Stream.concat(stream(keyData.identityCertChain()), stream(keyData.trustCerts()))
                .toArray(X509Certificate[]::new);
        return new InternalSSLKey(keyData.identityPrivateKey(), certChain);
    }
}
