package ee.ria.xroad.signer.protocol;

import io.grpc.Channel;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.signer.proto.SignerApiGrpc;

import static org.niis.xroad.signer.grpc.ServerCredentialsConfigurer.createClientCredentials;

@Slf4j
public class RpcSignerClient {
    @Getter
    private final SignerApiGrpc.SignerApiStub signerApiStub;
    @Getter
    private final SignerApiGrpc.SignerApiBlockingStub signerApiBlockingStub;

    /**
     * Construct client for accessing RouteGuide server using the existing channel.
     */
    public RpcSignerClient(Channel channel) {
        signerApiStub = SignerApiGrpc.newStub(channel);
        signerApiBlockingStub = SignerApiGrpc.newBlockingStub(channel);
    }

    /**
     * Greet server. If provided, the first element of {@code args} is the name to use in the
     * greeting.
     */
    public static RpcSignerClient init(int port) throws Exception {
        log.info("Starting grpc client init..");
        ManagedChannel channel = Grpc.newChannelBuilderForAddress("127.0.0.1", port, createClientCredentials())
                .build();

        RpcSignerClient client = new RpcSignerClient(channel);

        return client;
    }
}
