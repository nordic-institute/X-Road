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
package org.niis.xroad.common.rpc.credentials;

import io.grpc.ChannelCredentials;
import io.grpc.ServerCredentials;
import io.grpc.TlsChannelCredentials;
import io.grpc.TlsServerCredentials;
import io.grpc.util.AdvancedTlsX509TrustManager;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.rpc.VaultKeyProvider;

import java.security.cert.X509Certificate;
import java.util.Collection;

@RequiredArgsConstructor
public class TlsRpcCredentialsConfigurer implements RpcCredentialsConfigurer {
    private final VaultKeyProvider reloadableVaultKeyManager;

    @Override
    public ServerCredentials createServerCredentials() {
        TlsServerCredentials.Builder tlsBuilder = TlsServerCredentials.newBuilder()
                .keyManager(reloadableVaultKeyManager.getKeyManager())
                .trustManager(reloadableVaultKeyManager.getTrustManager())
                .clientAuth(TlsServerCredentials.ClientAuth.REQUIRE);

        return tlsBuilder.build();
    }

    @Override
    public ServerCredentials createServerCredentials(Collection<String> allowedClientNames) {
        if (allowedClientNames.isEmpty()) {
            return createServerCredentials();
        }
        try {
            var trustManager = reloadableVaultKeyManager.createVerifyingTrustManager(
                    new ClientIdentityMatcher(allowedClientNames));
            return TlsServerCredentials.newBuilder()
                    .keyManager(reloadableVaultKeyManager.getKeyManager())
                    .trustManager(trustManager)
                    .clientAuth(TlsServerCredentials.ClientAuth.REQUIRE)
                    .build();
        } catch (Exception e) {
            throw XrdRuntimeException.systemInternalError("Failed to create the server credentials", e);
        }
    }

    @Override
    public ChannelCredentials createClientCredentials() {

        TlsChannelCredentials.Builder tlsBuilder = TlsChannelCredentials.newBuilder()
                .keyManager(reloadableVaultKeyManager.getKeyManager())
                .trustManager(reloadableVaultKeyManager.getTrustManager());

        return tlsBuilder.build();
    }

    @Override
    public ChannelCredentials createClientCredentials(ChannelTrust trust) {
        return switch (trust) {
            case ChannelTrust.Shared _ -> createClientCredentials();
            case ChannelTrust.OnlyCa onlyCa -> createOnlyCaCredentials(onlyCa.ca());
        };
    }

    private ChannelCredentials createOnlyCaCredentials(X509Certificate ca) {
        try {
            var trustManager = AdvancedTlsX509TrustManager.newBuilder()
                    .setVerification(AdvancedTlsX509TrustManager.Verification.CERTIFICATE_AND_HOST_NAME_VERIFICATION)
                    .build();
            trustManager.updateTrustCredentials(new X509Certificate[] {ca});
            return TlsChannelCredentials.newBuilder()
                    .keyManager(reloadableVaultKeyManager.getKeyManager())
                    .trustManager(trustManager)
                    .build();
        } catch (Exception e) {
            throw XrdRuntimeException.systemInternalError("Failed to create the client credentials", e);
        }
    }
}
