package org.niis.xroad.signer.core.protocol;

import io.grpc.stub.StreamObserver;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.signer.core.protocol.handler.SignCertificateReqHandler;
import org.niis.xroad.signer.core.protocol.handler.SignReqHandler;
import org.niis.xroad.signer.proto.SignCertificateReq;
import org.niis.xroad.signer.proto.SignCertificateResp;
import org.niis.xroad.signer.proto.SignReq;
import org.niis.xroad.signer.proto.SignResp;
import org.niis.xroad.signer.proto.SignServiceGrpc;

@ApplicationScoped
@RequiredArgsConstructor
public class SignService extends SignServiceGrpc.SignServiceImplBase {
    private final SignReqHandler signReqHandler;
    private final SignCertificateReqHandler signCertificateReqHandler;

    @Override
    public void sign(SignReq request, StreamObserver<SignResp> responseObserver) {
        signReqHandler.processSingle(request, responseObserver);
    }

    @Override
    public void signCertificate(SignCertificateReq request, StreamObserver<SignCertificateResp> responseObserver) {
        signCertificateReqHandler.processSingle(request, responseObserver);
    }
}
