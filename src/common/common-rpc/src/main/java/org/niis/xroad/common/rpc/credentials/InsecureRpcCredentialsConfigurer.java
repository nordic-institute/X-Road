package org.niis.xroad.common.rpc.credentials;

import io.grpc.ChannelCredentials;
import io.grpc.InsecureChannelCredentials;
import io.grpc.InsecureServerCredentials;
import io.grpc.ServerCredentials;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class InsecureRpcCredentialsConfigurer implements RpcCredentialsConfigurer {
    @Override
    public ServerCredentials createServerCredentials() {

        log.warn("GRPC server is running without TLS. This is intended only for testing purposes.");
        return InsecureServerCredentials.create();
    }

    @Override
    public ChannelCredentials createClientCredentials() {
        log.warn("GRPC client is running without TLS. This is intended only for testing purposes.");
        return InsecureChannelCredentials.create();
    }
}
