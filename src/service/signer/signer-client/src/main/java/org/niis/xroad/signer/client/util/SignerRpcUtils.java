package org.niis.xroad.signer.client.util;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.ErrorCodes;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.experimental.UtilityClass;
import org.niis.xroad.rpc.error.CodedExceptionProto;
import org.niis.xroad.signer.api.exception.SignerException;

import java.util.function.Function;

import static ee.ria.xroad.common.ErrorCodes.SIGNER_X;
import static ee.ria.xroad.common.ErrorCodes.X_NETWORK_ERROR;

@UtilityClass
public class SignerRpcUtils {

    public static void tryToRun(Action action) throws SignerException {
        try {
            action.run();
        } catch (SignerException e) {
            throw e;
        } catch (CodedException e) {
            throw new SignerException(e);
        } catch (Exception e) {
            throw new SignerException(ErrorCodes.X_INTERNAL_ERROR, e);
        }
    }

    public static <R, T> T tryToRun(ActionWithResult<R> action, Function<R, T> mapper) throws SignerException {
        return tryToRun(() -> mapper.apply(action.run()));
    }

    public static <T> T tryToRun(ActionWithResult<T> action) throws SignerException {
        try {
            return action.run();
        } catch (SignerException e) {
            throw e;
        } catch (CodedException e) {
            throw new SignerException(e);
        } catch (StatusRuntimeException error) {
            if (error.getStatus().getCode() == Status.Code.DEADLINE_EXCEEDED) {
                throw SignerException.tr(X_NETWORK_ERROR, "signer_client_timeout",
                                "Signer client timed out. " + error.getStatus().getDescription())
                        .withPrefix(SIGNER_X);
            }
            com.google.rpc.Status status = io.grpc.protobuf.StatusProto.fromThrowable(error);
            if (status != null) {
                handleGenericStatusRuntimeException(status);
            }
            throw error;
        } catch (Exception e) {
            throw new SignerException(ErrorCodes.X_INTERNAL_ERROR, e);
        }
    }

    private static void handleGenericStatusRuntimeException(com.google.rpc.Status status) {
        for (Any any : status.getDetailsList()) {
            if (any.is(CodedExceptionProto.class)) {
                try {
                    final CodedExceptionProto ce = any.unpack(CodedExceptionProto.class);
                    throw CodedException.tr(ce.getFaultCode(), ce.getTranslationCode(), ce.getFaultString())
                            .withPrefix(SIGNER_X);
                } catch (InvalidProtocolBufferException e) {
                    throw new RuntimeException("Failed to parse grpc message", e);
                }
            }
        }
    }

    public interface ActionWithResult<T> {
        T run() throws Exception;
    }

    public interface Action {
        void run() throws Exception;
    }
}
