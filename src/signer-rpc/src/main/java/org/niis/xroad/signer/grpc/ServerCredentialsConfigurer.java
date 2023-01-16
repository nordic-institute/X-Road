package org.niis.xroad.signer.grpc;

import io.grpc.ChannelCredentials;
import io.grpc.InsecureChannelCredentials;
import io.grpc.InsecureServerCredentials;
import io.grpc.ServerCredentials;
import io.grpc.TlsChannelCredentials;
import io.grpc.TlsServerCredentials;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.io.File;
import java.io.IOException;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ServerCredentialsConfigurer {
    //TODO will be enabled in live env.
    private static final boolean USE_TLS = false;

    public static ServerCredentials createServerCredentials() throws IOException {
        if (USE_TLS) {
            //TODO fill to use tls auth.
            File certChain = null;
            File privateKey = null;
            String privateKeyPassword = null;
            File trustRootCert = null;

            TlsServerCredentials.Builder tlsBuilder = TlsServerCredentials.newBuilder()
                    .keyManager(certChain, privateKey, privateKeyPassword)
                    .trustManager(trustRootCert)
                    .clientAuth(TlsServerCredentials.ClientAuth.REQUIRE);

            return tlsBuilder.build();
        } else {
            return InsecureServerCredentials.create();
        }
    }

    public static ChannelCredentials createClientCredentials() throws IOException {
        if (USE_TLS) {
            //TODO fill to use tls auth.
            File certChain = null;
            File privateKey = null;
            String privateKeyPassword = null;
            File trustRootCert = null;

            TlsChannelCredentials.Builder tlsBuilder = TlsChannelCredentials.newBuilder()
                    .keyManager(certChain, privateKey, privateKeyPassword)
                    .trustManager(trustRootCert);

            return tlsBuilder.build();
        } else {
            return InsecureChannelCredentials.create();
        }
    }
}
