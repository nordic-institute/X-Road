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

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.signer.TemporaryHelper;
import ee.ria.xroad.signer.protocol.dto.TokenInfoAndKeyIdProto;
import ee.ria.xroad.signer.protocol.dto.TokenInfoProto;
import ee.ria.xroad.signer.protocol.message.ActivateToken;
import ee.ria.xroad.signer.tokenmanager.TokenManager;
import ee.ria.xroad.signer.tokenmanager.token.AbstractTokenWorker;
import ee.ria.xroad.signer.tokenmanager.token.SoftwareTokenWorker;

import com.google.protobuf.AbstractMessage;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.signer.proto.ActivateTokenRequest;
import org.niis.xroad.signer.proto.GetTokenBatchSigningEnabledRequest;
import org.niis.xroad.signer.proto.GetTokenBatchSigningEnabledResponse;
import org.niis.xroad.signer.proto.GetTokenByCertHashRequest;
import org.niis.xroad.signer.proto.GetTokenByCertRequestIdRequest;
import org.niis.xroad.signer.proto.GetTokenByIdRequest;
import org.niis.xroad.signer.proto.GetTokenByKeyIdRequest;
import org.niis.xroad.signer.proto.InitSoftwareTokenRequest;
import org.niis.xroad.signer.proto.ListTokensResponse;
import org.niis.xroad.signer.proto.SetTokenFriendlyNameRequest;
import org.niis.xroad.signer.proto.TokenServiceGrpc;
import org.niis.xroad.signer.proto.UpdateSoftwareTokenPinRequest;
import org.niis.xroad.signer.protocol.dto.Empty;
import org.springframework.stereotype.Service;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;

/**
 * Handles requests for token list.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokensService extends TokenServiceGrpc.TokenServiceImplBase {
    private final TemporaryAkkaMessenger temporaryAkkaMessenger;

    @Override
    public void listTokens(Empty request, StreamObserver<ListTokensResponse> responseObserver) {
        final ListTokensResponse.Builder builder = ListTokensResponse.newBuilder();

        TokenManager.listTokens().forEach(tokenInfo -> builder.addTokens(tokenInfo.asMessage()));

        emitSingleAndClose(responseObserver, builder.build());
    }

    @SneakyThrows
    @Override
    public void activateToken(ActivateTokenRequest request, StreamObserver<Empty> responseObserver) {
        ActivateToken actorMsg = new ActivateToken(request.getTokenId(), request.getActivate());

        final AbstractTokenWorker tokenWorker = TemporaryHelper.getTokenWorker(request.getTokenId());
        tokenWorker.handleActivateToken(actorMsg);

        emitSingleAndClose(responseObserver, Empty.getDefaultInstance());
    }

    @Override
    public void getTokenById(GetTokenByIdRequest request, StreamObserver<TokenInfoProto> responseObserver) {
        var token = TokenManager.findTokenInfo(request.getTokenId());
        emitSingleAndClose(responseObserver, token.asMessage());
    }

    @Override
    public void getTokenByKey(GetTokenByKeyIdRequest request, StreamObserver<TokenInfoProto> responseObserver) {
        var token = TokenManager.findTokenInfoForKeyId(request.getKeyId());
        emitSingleAndClose(responseObserver, token.asMessage());
    }

    @Override
    public void getTokenAndKeyIdByCertRequestId(GetTokenByCertRequestIdRequest request, StreamObserver<TokenInfoAndKeyIdProto> responseObserver) {
        var token = TokenManager.findTokenAndKeyIdForCertRequestId(request.getCertRequestId());
        emitSingleAndClose(responseObserver, token.asMessage());
    }

    @Override
    public void getTokenAndKeyIdByCertHash(GetTokenByCertHashRequest request, StreamObserver<TokenInfoAndKeyIdProto> responseObserver) {
        var token = TokenManager.findTokenAndKeyIdForCertHash(request.getCertHash());
        emitSingleAndClose(responseObserver, token.asMessage());
    }

    @Override
    public void setTokenFriendlyName(SetTokenFriendlyNameRequest request, StreamObserver<Empty> responseObserver) {
        TokenManager.setTokenFriendlyName(
                request.getTokenId(),
                request.getFriendlyName());

        emitSingleAndClose(responseObserver, Empty.getDefaultInstance());
    }

    @Override
    public void getTokenBatchSigningEnabled(GetTokenBatchSigningEnabledRequest request, StreamObserver<GetTokenBatchSigningEnabledResponse> responseObserver) {
        String tokenId = TokenManager.findTokenIdForKeyId(request.getKeyId());

        emitSingleAndClose(responseObserver, GetTokenBatchSigningEnabledResponse.newBuilder()
                .setBatchingSigningEnabled(TokenManager.isBatchSigningEnabled(tokenId))
                .build());
    }

    @Override
    public void initSoftwareToken(InitSoftwareTokenRequest request, StreamObserver<Empty> responseObserver) {
        String softwareTokenId = TokenManager.getSoftwareTokenId();

        if (softwareTokenId != null) {

            final AbstractTokenWorker tokenWorker = TemporaryHelper.getTokenWorker(softwareTokenId);
            if (tokenWorker instanceof SoftwareTokenWorker) {
                try {
                    ((SoftwareTokenWorker) tokenWorker).initializeToken(request.getPin().toCharArray());
                } catch (Exception e) {
                    throw new CodedException(X_INTERNAL_ERROR, e); //todo move to worker
                }
                emitSingleAndClose(responseObserver, Empty.getDefaultInstance());
            } else {
                throw new CodedException(X_INTERNAL_ERROR, "Software token not found");
            }
        } else {
            throw new CodedException(X_INTERNAL_ERROR, "Software token not found");
        }
    }

    @Override
    public void updateSoftwareTokenPin(UpdateSoftwareTokenPinRequest request, StreamObserver<Empty> responseObserver) {
        final AbstractTokenWorker tokenWorker = TemporaryHelper.getTokenWorker(request.getTokenId());
        if (tokenWorker instanceof SoftwareTokenWorker) {
            try {
                ((SoftwareTokenWorker) tokenWorker).handleUpdateTokenPin(request.getOldPin().toCharArray(), request.getNewPin().toCharArray());
            } catch (Exception e) {
                // todo move to tokenworker
                throw new CodedException(X_INTERNAL_ERROR, e);
            }
        } else {
            throw new CodedException(X_INTERNAL_ERROR, "Software token not found");
        }

        emitSingleAndClose(responseObserver, Empty.getDefaultInstance());
    }

    private <T extends AbstractMessage> void emitSingleAndClose(StreamObserver<T> responseObserver, T value) {
        responseObserver.onNext(value);
        responseObserver.onCompleted();
    }


}