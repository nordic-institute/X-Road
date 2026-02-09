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

import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.Optional;

public class NoopVaultClient implements VaultClient {
    @Override
    public InternalSSLKey getInternalTlsCredentials() {
        return new InternalSSLKey(null, new X509Certificate[]{});
    }

    @Override
    public InternalSSLKey getOpmonitorTlsCredentials() {
        return new InternalSSLKey(null, new X509Certificate[]{});
    }

    @Override
    public InternalSSLKey getAdminServiceTlsCredentials() {
        return new InternalSSLKey(null, new X509Certificate[]{});
    }

    @Override
    public InternalSSLKey getManagementServicesTlsCredentials() {
        return new InternalSSLKey(null, new X509Certificate[]{});
    }

    @Override
    public void createInternalTlsCredentials(InternalSSLKey internalSSLKey) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void createOpmonitorTlsCredentials(InternalSSLKey internalSSLKey) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void createAdminServiceTlsCredentials(InternalSSLKey internalSSLKey) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void createManagementServiceTlsCredentials(InternalSSLKey internalSSLKey) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void setMLogArchivalSigningSecretKey(String armoredPrivateKey) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public Optional<String> getMLogArchivalSigningSecretKey() {
        return Optional.empty();
    }

    @Override
    public void setMLogArchivalEncryptionPublicKeys(String armoredRecipientPublicKeys) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public Optional<String> getMLogArchivalEncryptionPublicKeys() {
        return Optional.empty();
    }

    @Override
    public void setMLogDBEncryptionSecretKey(String keyId, String base64SecretKey) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public Map<String, String> getMLogDBEncryptionSecretKeys() {
        return Map.of();
    }

    @Override
    public void setTokenPin(String tokenId, char[] pin) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public Optional<char[]> getTokenPin(String tokenId) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void deleteTokenPin(String tokenId) {
        throw new UnsupportedOperationException("Not supported");
    }
}
