package ee.ria.xroad.signer.protocol;

import ee.ria.xroad.signer.protocol.message.GetOcspResponses;
import ee.ria.xroad.signer.protocol.message.SetOcspResponses;

import com.google.protobuf.AbstractMessage;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.signer.proto.GetOcspResponsesRequest;
import org.niis.xroad.signer.proto.GetOcspResponsesResponse;
import org.niis.xroad.signer.proto.OcspServiceGrpc;
import org.niis.xroad.signer.proto.SetOcspResponsesRequest;
import org.niis.xroad.signer.protocol.dto.Empty;
import org.springframework.stereotype.Service;

import static java.util.Arrays.asList;

@Slf4j
@Service
@RequiredArgsConstructor
public class OcspService extends OcspServiceGrpc.OcspServiceImplBase {
    private final TemporaryAkkaMessenger temporaryAkkaMessenger;

    @Override
    public void setOcspResponses(SetOcspResponsesRequest request, StreamObserver<Empty> responseObserver) {
        var message = new SetOcspResponses(
                request.getCertHashesList().toArray(new String[0]),
                request.getBase64EncodedResponsesList().toArray(new String[0]));

        temporaryAkkaMessenger.tellOcspManager(message);
        emitSingleAndClose(responseObserver, Empty.getDefaultInstance());
    }

    @Override
    public void getOcspResponses(GetOcspResponsesRequest request, StreamObserver<GetOcspResponsesResponse> responseObserver) {
        var message = new GetOcspResponses(
                request.getCertHashList().toArray(new String[0]));

        ee.ria.xroad.signer.protocol.message.GetOcspResponsesResponse response = temporaryAkkaMessenger.tellOcspManagerWithResponse(message);
        emitSingleAndClose(responseObserver, GetOcspResponsesResponse.newBuilder()
                .addAllBase64EncodedResponses(asList(response.getBase64EncodedResponses()))
                .build());
    }

    private <T extends AbstractMessage> void emitSingleAndClose(StreamObserver<T> responseObserver, T value) {
        responseObserver.onNext(value);
        responseObserver.onCompleted();
    }
}
