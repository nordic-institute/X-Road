package ee.ria.xroad.signer.protocol;

import ee.ria.xroad.signer.protocol.handler.GetOcspResponsesReqHandler;
import ee.ria.xroad.signer.protocol.handler.SetOcspResponsesReqHandler;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.signer.proto.GetOcspResponsesReq;
import org.niis.xroad.signer.proto.GetOcspResponsesResp;
import org.niis.xroad.signer.proto.OcspServiceGrpc;
import org.niis.xroad.signer.proto.SetOcspResponsesReq;
import org.niis.xroad.signer.protocol.dto.Empty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OcspService extends OcspServiceGrpc.OcspServiceImplBase {
    private final SetOcspResponsesReqHandler setOcspResponsesReqHandler;
    private final GetOcspResponsesReqHandler getOcspResponsesReqHandler;

    @Override
    public void setOcspResponses(SetOcspResponsesReq request, StreamObserver<Empty> responseObserver) {
        setOcspResponsesReqHandler.processSingle(request, responseObserver);
    }

    @Override
    public void getOcspResponses(GetOcspResponsesReq request, StreamObserver<GetOcspResponsesResp> responseObserver) {
        getOcspResponsesReqHandler.processSingle(request, responseObserver);
    }

}
