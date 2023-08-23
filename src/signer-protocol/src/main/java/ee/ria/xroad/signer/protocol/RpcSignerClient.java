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
package ee.ria.xroad.signer.protocol;

import io.grpc.Channel;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.signer.proto.CertificateServiceGrpc;
import org.niis.xroad.signer.proto.KeyServiceGrpc;
import org.niis.xroad.signer.proto.OcspServiceGrpc;
import org.niis.xroad.signer.proto.TokenServiceGrpc;

import static org.niis.xroad.signer.grpc.ServerCredentialsConfigurer.createClientCredentials;

@Slf4j
public class RpcSignerClient {
    @Getter
    private final TokenServiceGrpc.TokenServiceStub signerApiStub;
    @Getter
    private final TokenServiceGrpc.TokenServiceBlockingStub signerApiBlockingStub;
    @Getter
    private final CertificateServiceGrpc.CertificateServiceBlockingStub certificateServiceBlockingStub;
    @Getter
    private final KeyServiceGrpc.KeyServiceBlockingStub keyServiceBlockingStub;
    @Getter
    private final OcspServiceGrpc.OcspServiceBlockingStub ocspServiceBlockingStub;

    /**
     * Construct client for accessing RouteGuide server using the existing channel.
     */
    public RpcSignerClient(Channel channel) {
        signerApiStub = TokenServiceGrpc.newStub(channel);
        signerApiBlockingStub = TokenServiceGrpc.newBlockingStub(channel);
        certificateServiceBlockingStub = CertificateServiceGrpc.newBlockingStub(channel);
        keyServiceBlockingStub = KeyServiceGrpc.newBlockingStub(channel);
        ocspServiceBlockingStub = OcspServiceGrpc.newBlockingStub(channel);
    }

    /**
     * Greet server. If provided, the first element of {@code args} is the name to use in the
     * greeting.
     */
    public static RpcSignerClient init(int port) throws Exception {
        var credentials = createClientCredentials();
        log.info("Starting grpc client with {} credentials..", credentials.getClass().getSimpleName());
        ManagedChannel channel = Grpc.newChannelBuilderForAddress("127.0.0.1", port, credentials)
                .build();

        return new RpcSignerClient(channel);
    }
}
