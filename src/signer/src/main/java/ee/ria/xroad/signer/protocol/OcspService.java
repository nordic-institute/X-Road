package ee.ria.xroad.signer.protocol;

import ee.ria.xroad.signer.protocol.handler.GetOcspResponsesRequestHandler;
import ee.ria.xroad.signer.protocol.handler.SetOcspResponsesRequestHandler;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.signer.proto.GetOcspResponsesRequest;
import org.niis.xroad.signer.proto.GetOcspResponsesResponse;
import org.niis.xroad.signer.proto.OcspServiceGrpc;
import org.niis.xroad.signer.proto.SetOcspResponsesRequest;
import org.niis.xroad.signer.protocol.dto.Empty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OcspService extends OcspServiceGrpc.OcspServiceImplBase {
    private final SetOcspResponsesRequestHandler setOcspResponsesRequestHandler;
    private final GetOcspResponsesRequestHandler getOcspResponsesRequestHandler;

    @Override
    public void setOcspResponses(SetOcspResponsesRequest request, StreamObserver<Empty> responseObserver) {
        setOcspResponsesRequestHandler.processSingle(request, responseObserver);
    }

    @Override
    public void getOcspResponses(GetOcspResponsesRequest request, StreamObserver<GetOcspResponsesResponse> responseObserver) {
        getOcspResponsesRequestHandler.processSingle(request, responseObserver);
    }

}
