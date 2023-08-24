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

import ee.ria.xroad.signer.protocol.dto.TokenInfoAndKeyIdProto;
import ee.ria.xroad.signer.protocol.dto.TokenInfoProto;
import ee.ria.xroad.signer.protocol.handler.ActivateTokenRequestHandler;
import ee.ria.xroad.signer.protocol.handler.GetTokenBatchSigningEnabledRequestHandler;
import ee.ria.xroad.signer.protocol.handler.GetTokenInfoAndKeyIdForCertHashRequestHandler;
import ee.ria.xroad.signer.protocol.handler.GetTokenInfoAndKeyIdForCertRequestIdRequestHandler;
import ee.ria.xroad.signer.protocol.handler.GetTokenInfoForKeyIdRequestHandler;
import ee.ria.xroad.signer.protocol.handler.GetTokenInfoRequestHandler;
import ee.ria.xroad.signer.protocol.handler.InitSoftwareTokenRequestHandler;
import ee.ria.xroad.signer.protocol.handler.ListTokensRequestHandler;
import ee.ria.xroad.signer.protocol.handler.SetTokenFriendlyNameRequestHandler;
import ee.ria.xroad.signer.protocol.handler.UpdateSoftwareTokenPinRequestHandler;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
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

/**
 * Token gRPC service.
 */
@Service
@RequiredArgsConstructor
public class TokensService extends TokenServiceGrpc.TokenServiceImplBase {
    private final ActivateTokenRequestHandler activateTokenRequestHandler;
    private final UpdateSoftwareTokenPinRequestHandler updateSoftwareTokenPinRequestHandler;
    private final InitSoftwareTokenRequestHandler initSoftwareTokenRequestHandler;
    private final GetTokenInfoRequestHandler getTokenInfoRequestHandler;
    private final GetTokenInfoForKeyIdRequestHandler getTokenInfoForKeyIdRequestHandler;
    private final GetTokenBatchSigningEnabledRequestHandler getTokenBatchSigningEnabledRequestHandler;
    private final GetTokenInfoAndKeyIdForCertHashRequestHandler getTokenInfoAndKeyIdForCertHashRequestHandler;
    private final GetTokenInfoAndKeyIdForCertRequestIdRequestHandler getTokenInfoAndKeyIdForCertRequestIdRequestHandler;
    private final SetTokenFriendlyNameRequestHandler setTokenFriendlyNameRequestHandler;
    private final ListTokensRequestHandler listTokensRequestHandler;

    @Override
    public void listTokens(Empty request, StreamObserver<ListTokensResponse> responseObserver) {
        listTokensRequestHandler.processSingle(request, responseObserver);
    }

    @Override
    public void activateToken(ActivateTokenRequest request, StreamObserver<Empty> responseObserver) {
        activateTokenRequestHandler.processSingle(request, responseObserver);
    }

    @Override
    public void getTokenById(GetTokenByIdRequest request, StreamObserver<TokenInfoProto> responseObserver) {
        getTokenInfoRequestHandler.processSingle(request, responseObserver);
    }

    @Override
    public void getTokenByKey(GetTokenByKeyIdRequest request, StreamObserver<TokenInfoProto> responseObserver) {
        getTokenInfoForKeyIdRequestHandler.processSingle(request, responseObserver);
    }

    @Override
    public void getTokenAndKeyIdByCertRequestId(GetTokenByCertRequestIdRequest request,
                                                StreamObserver<TokenInfoAndKeyIdProto> responseObserver) {
        getTokenInfoAndKeyIdForCertRequestIdRequestHandler.processSingle(request, responseObserver);
    }

    @Override
    public void getTokenAndKeyIdByCertHash(GetTokenByCertHashRequest request, StreamObserver<TokenInfoAndKeyIdProto> responseObserver) {
        getTokenInfoAndKeyIdForCertHashRequestHandler.processSingle(request, responseObserver);
    }

    @Override
    public void setTokenFriendlyName(SetTokenFriendlyNameRequest request, StreamObserver<Empty> responseObserver) {
        setTokenFriendlyNameRequestHandler.processSingle(request, responseObserver);
    }

    @Override
    public void getTokenBatchSigningEnabled(GetTokenBatchSigningEnabledRequest request,
                                            StreamObserver<GetTokenBatchSigningEnabledResponse> responseObserver) {
        getTokenBatchSigningEnabledRequestHandler.processSingle(request, responseObserver);
    }

    @Override
    public void initSoftwareToken(InitSoftwareTokenRequest request, StreamObserver<Empty> responseObserver) {
        initSoftwareTokenRequestHandler.processSingle(request, responseObserver);
    }

    @Override
    public void updateSoftwareTokenPin(UpdateSoftwareTokenPinRequest request, StreamObserver<Empty> responseObserver) {
        updateSoftwareTokenPinRequestHandler.processSingle(request, responseObserver);
    }

}
