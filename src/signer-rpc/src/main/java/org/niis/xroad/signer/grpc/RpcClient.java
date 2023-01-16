package org.niis.xroad.signer.grpc;

import io.grpc.Channel;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.signer.proto.SignerApiGrpc;

import static org.niis.xroad.signer.grpc.ServerCredentialsConfigurer.createClientCredentials;

@Slf4j
public class RpcClient {
    @Getter
    private final SignerApiGrpc.SignerApiStub signerApiStub;
    @Getter
    private final SignerApiGrpc.SignerApiBlockingStub signerApiBlockingStub;
    /**
     * Construct client for accessing RouteGuide server using the existing channel.
     */
    public RpcClient(Channel channel) {
        signerApiStub = SignerApiGrpc.newStub(channel);
        signerApiBlockingStub = SignerApiGrpc.newBlockingStub(channel);
    }

    /**
     * Greet server. If provided, the first element of {@code args} is the name to use in the
     * greeting.
     */
    public static RpcClient init(int port) throws Exception {
        ManagedChannel channel = Grpc.newChannelBuilderForAddress("127.0.0.1", port, createClientCredentials())
//                .overrideAuthority("foo.test.google.fr")
                .build();

        RpcClient client = new RpcClient(channel);

        return client;
    }
}
