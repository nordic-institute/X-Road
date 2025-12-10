package org.niis.xroad.signer.softtoken.protocol;

import io.grpc.stub.StreamObserver;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.signer.proto.SignReq;
import org.niis.xroad.signer.proto.SignResp;
import org.niis.xroad.signer.proto.SignServiceGrpc;
import org.niis.xroad.signer.softtoken.protocol.handler.SoftwareTokenSignReqHandler;

@ApplicationScoped
@RequiredArgsConstructor
public class SignService extends SignServiceGrpc.SignServiceImplBase {
    private final SoftwareTokenSignReqHandler signReqHandler;

    @Override
    public void sign(SignReq request, StreamObserver<SignResp> responseObserver) {
        signReqHandler.processSingle(request, responseObserver);
    }
}
