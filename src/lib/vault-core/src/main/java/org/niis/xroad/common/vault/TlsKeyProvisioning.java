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

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.stream.Stream;

import static java.util.Arrays.stream;
import static org.niis.xroad.common.core.exception.ErrorCode.MISSING_SECRET;

/**
 * Reads a TLS key from the secret store and provisions a new one only when the store reports the key absent.
 * Any other failure, such as the store being unreachable, sealed or failing over, is passed on: it never leads
 * to a key being created or replaced.
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
        var keyData = keyClient.provisionNewCerts();
        var certChain = Stream.concat(stream(keyData.identityCertChain()), stream(keyData.trustCerts()))
                .toArray(X509Certificate[]::new);
        var tlsKey = new InternalSSLKey(keyData.identityPrivateKey(), certChain);
        writer.write(tlsKey);
        log.info("Successfully created '{}' TLS credentials", name);
        return tlsKey;
    }
}
