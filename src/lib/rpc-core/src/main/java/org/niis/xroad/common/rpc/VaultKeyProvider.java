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

package org.niis.xroad.common.rpc;

import io.grpc.util.AdvancedTlsX509TrustManager;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;

public interface VaultKeyProvider {

    KeyManager getKeyManager();

    TrustManager getTrustManager();

    /**
     * Creates a trust manager with the same trust anchors as {@link #getTrustManager()} that also runs the given
     * verifier after the chain check. It is updated together with the shared trust manager.
     *
     * @param verifier additional peer check
     * @return the dedicated trust manager
     */
    default AdvancedTlsX509TrustManager createVerifyingTrustManager(
            AdvancedTlsX509TrustManager.SslSocketAndEnginePeerVerifier verifier) {
        throw new UnsupportedOperationException("Not supported");
    }

    void init();

    void shutdown();

}
