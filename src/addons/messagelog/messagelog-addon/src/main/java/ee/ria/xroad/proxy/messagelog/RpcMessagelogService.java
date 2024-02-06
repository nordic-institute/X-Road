/*
 * The MIT License
 *
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

package ee.ria.xroad.proxy.messagelog;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.messagelog.MessageRecord;

import io.grpc.Status;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.rpc.mapper.ClientIdMapper;
import org.niis.xroad.common.rpc.mapper.ServiceIdMapper;
import org.niis.xroad.messagelog.proto.LogMessageReq;
import org.niis.xroad.messagelog.proto.MessagelogServiceGrpc;
import org.niis.xroad.messagelog.proto.SignatureData;
import org.niis.xroad.rpc.error.CodedExceptionProto;
import org.niis.xroad.signer.protocol.dto.Empty;

import java.util.Date;

import static com.google.protobuf.Any.pack;
import static java.util.Optional.ofNullable;

@RequiredArgsConstructor
@Slf4j
public class RpcMessagelogService extends MessagelogServiceGrpc.MessagelogServiceImplBase {

    private final LogManager logManager;

    @Override
    public void log(LogMessageReq request, StreamObserver<Empty> responseObserver) {
        try {
            logManager.log(convertToMessageRecord(request));

            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (Exception exception) {
            handleException(exception, responseObserver);
        }
    }

    private MessageRecord convertToMessageRecord(LogMessageReq request) throws Exception {

        MessageRecord messageRecord = new MessageRecord(request.getQueryId(),
                request.getBody(),
                request.getSignature().getSignatureXml(),
                request.getResponse(),
                request.getClientSide() ? ClientIdMapper.fromDto(request.getClientId())
                        : ServiceIdMapper.fromDto(request.getServiceId()).getClientId(),
                request.getXRequestId());

        messageRecord.setTime(new Date().getTime());

        // todo: handle message body and attachments
        // see ee.ria.xroad.proxy.messagelog.LogManager.createMessageRecord(ee.ria.xroad.common.messagelog.RestLogMessage)

        if (isBatchSignature(request.getSignature())) {
            messageRecord.setHashChainResult(request.getSignature().getHashChainResult());
            messageRecord.setHashChain(request.getSignature().getHashChain());
        }

        messageRecord.setSignatureHash(LogManager.signatureHash(request.getSignature().getSignatureXml()));

        return messageRecord;
    }

    private boolean isBatchSignature(SignatureData signature) {
        return signature.hasHashChainResult() && signature.hasHashChain();

    }

    private void handleException(Exception exception, StreamObserver<Empty> responseObserver) {
        if (exception instanceof CodedException codedException) {
            com.google.rpc.Status status = com.google.rpc.Status.newBuilder()
                    .setCode(Status.Code.INTERNAL.value())
                    .setMessage(codedException.getMessage())
                    .addDetails(pack(toProto(codedException)))
                    .build();

            responseObserver.onError(StatusProto.toStatusRuntimeException(status));
        } else {
            log.warn("Exception while logging to messagelog", exception);
            responseObserver.onError(exception);
        }
    }

    // todo: duplicate, move to commons
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
