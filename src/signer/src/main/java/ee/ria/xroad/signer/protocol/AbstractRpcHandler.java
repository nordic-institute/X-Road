package ee.ria.xroad.signer.protocol;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.signer.protocol.dto.CodedExceptionProto;

import com.google.protobuf.AbstractMessage;
import io.grpc.Status;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import static com.google.protobuf.Any.pack;
import static java.util.Optional.ofNullable;

/**
 * @param <R>
 * @param <T>
 */
@Slf4j
public abstract class AbstractRpcHandler<R extends AbstractMessage, T extends AbstractMessage> {
    @Autowired
    protected TemporaryAkkaMessenger temporaryAkkaMessenger;

    protected abstract T handle(R request) throws Exception;

    public void processSingle(R request, StreamObserver<T> responseObserver) {
        try {
            var response = handle(request);

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleException(e, responseObserver);
        }
    }

    private void handleException(Exception exception, StreamObserver<T> responseObserver) {
        if (exception instanceof CodedException) {
            CodedException codedException = (CodedException) exception;

            com.google.rpc.Status status = com.google.rpc.Status.newBuilder()
                    .setCode(Status.Code.INTERNAL.value())
                    .setMessage(codedException.getMessage())
                    .addDetails(pack(toProto(codedException)))
                    .build();

            responseObserver.onError(StatusProto.toStatusRuntimeException(status));
        } else {
            log.warn("Unhandled exception was thrown by gRPC handler.", exception);
            responseObserver.onError(exception);
        }
    }

    private CodedExceptionProto toProto(CodedException codedException) {
        final CodedExceptionProto.Builder codedExceptionBuilder = CodedExceptionProto.newBuilder();

        ofNullable(codedException.getFaultCode()).ifPresent(codedExceptionBuilder::setFaultCode);
        ofNullable(codedException.getFaultActor()).ifPresent(codedExceptionBuilder::setFaultActor);
        ofNullable(codedException.getFaultDetail()).ifPresent(codedExceptionBuilder::setFaultDetail);
        ofNullable(codedException.getFaultString()).ifPresent(codedExceptionBuilder::setFaultString);
        ofNullable(codedException.getTranslationCode()).ifPresent(codedExceptionBuilder::setTranslationCode);

        return codedExceptionBuilder.build();
    }
}
