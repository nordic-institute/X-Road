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
package org.niis.xroad.common.rpc.test;

import io.grpc.util.AdvancedTlsX509KeyManager;
import io.grpc.util.AdvancedTlsX509TrustManager;
import org.niis.xroad.common.rpc.VaultKeyProvider;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;

import java.security.cert.X509Certificate;

/**
 * A {@link VaultKeyProvider} holding a fixed identity and fixed trust anchors, standing in for the reloadable
 * OpenBao-backed key manager of a component.
 */
public final class TestVaultKeyProvider implements VaultKeyProvider {

    private final AdvancedTlsX509KeyManager keyManager = new AdvancedTlsX509KeyManager();
    private final AdvancedTlsX509TrustManager trustManager = AdvancedTlsX509TrustManager.newBuilder()
            .setVerification(AdvancedTlsX509TrustManager.Verification.CERTIFICATE_AND_HOST_NAME_VERIFICATION)
            .build();

    private final X509Certificate[] trustAnchors;

    public TestVaultKeyProvider(TestCa.TestLeaf identity, X509Certificate... trustAnchors) throws Exception {
        this.trustAnchors = trustAnchors;
        keyManager.updateIdentityCredentials(identity.chain(), identity.privateKey());
        trustManager.updateTrustCredentials(trustAnchors);
    }

    @Override
    public KeyManager getKeyManager() {
        return keyManager;
    }

    @Override
    public TrustManager getTrustManager() {
        return trustManager;
    }

    @Override
    public AdvancedTlsX509TrustManager createVerifyingTrustManager(
            AdvancedTlsX509TrustManager.SslSocketAndEnginePeerVerifier verifier) {
        try {
            var verifyingTrustManager = AdvancedTlsX509TrustManager.newBuilder()
                    .setVerification(AdvancedTlsX509TrustManager.Verification.CERTIFICATE_AND_HOST_NAME_VERIFICATION)
                    .setSslSocketAndEnginePeerVerifier(verifier)
                    .build();
            verifyingTrustManager.updateTrustCredentials(trustAnchors);
            return verifyingTrustManager;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create the verifying trust manager", e);
        }
    }

    @Override
    public void init() {
        // nothing to load
    }

    @Override
    public void shutdown() {
        // nothing to release
    }
}
