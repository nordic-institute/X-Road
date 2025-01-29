package org.niis.xroad.common.rpc.credentials;

import io.grpc.ChannelCredentials;
import io.grpc.ServerCredentials;
import io.grpc.TlsChannelCredentials;
import io.grpc.TlsServerCredentials;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.rpc.VaultKeyProvider;

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
    public ChannelCredentials createClientCredentials() {

        TlsChannelCredentials.Builder tlsBuilder = TlsChannelCredentials.newBuilder()
                .keyManager(reloadableVaultKeyManager.getKeyManager())
                .trustManager(reloadableVaultKeyManager.getTrustManager());

        return tlsBuilder.build();
    }
}
