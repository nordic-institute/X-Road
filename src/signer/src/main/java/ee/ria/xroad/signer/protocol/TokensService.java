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
import ee.ria.xroad.signer.protocol.handler.ActivateTokenReqHandler;
import ee.ria.xroad.signer.protocol.handler.GetHSMOperationalInfoReqHandler;
import ee.ria.xroad.signer.protocol.handler.GetMemberSigningInfoReqHandler;
import ee.ria.xroad.signer.protocol.handler.GetTokenBatchSigningEnabledReqHandler;
import ee.ria.xroad.signer.protocol.handler.GetTokenInfoAndKeyIdForCertHashReqHandler;
import ee.ria.xroad.signer.protocol.handler.GetTokenInfoAndKeyIdForCertRequestIdReqHandler;
import ee.ria.xroad.signer.protocol.handler.GetTokenInfoForKeyIdReqHandler;
import ee.ria.xroad.signer.protocol.handler.GetTokenInfoReqHandler;
import ee.ria.xroad.signer.protocol.handler.InitSoftwareTokenReqHandler;
import ee.ria.xroad.signer.protocol.handler.ListTokensReqHandler;
import ee.ria.xroad.signer.protocol.handler.SetTokenFriendlyNameReqHandler;
import ee.ria.xroad.signer.protocol.handler.UpdateSoftwareTokenPinReqHandler;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.signer.proto.ActivateTokenReq;
import org.niis.xroad.signer.proto.GetHSMOperationalInfoResp;
import org.niis.xroad.signer.proto.GetMemberSigningInfoReq;
import org.niis.xroad.signer.proto.GetMemberSigningInfoResp;
import org.niis.xroad.signer.proto.GetTokenBatchSigningEnabledReq;
import org.niis.xroad.signer.proto.GetTokenBatchSigningEnabledResp;
import org.niis.xroad.signer.proto.GetTokenByCertHashReq;
import org.niis.xroad.signer.proto.GetTokenByCertRequestIdReq;
import org.niis.xroad.signer.proto.GetTokenByIdReq;
import org.niis.xroad.signer.proto.GetTokenByKeyIdReq;
import org.niis.xroad.signer.proto.InitSoftwareTokenReq;
import org.niis.xroad.signer.proto.ListTokensResp;
import org.niis.xroad.signer.proto.SetTokenFriendlyNameReq;
import org.niis.xroad.signer.proto.TokenServiceGrpc;
import org.niis.xroad.signer.proto.UpdateSoftwareTokenPinReq;
import org.niis.xroad.signer.protocol.dto.Empty;
import org.springframework.stereotype.Service;

/**
 * Token gRPC service.
 */
@Service
@RequiredArgsConstructor
public class TokensService extends TokenServiceGrpc.TokenServiceImplBase {
    private final ActivateTokenReqHandler activateTokenReqHandler;
    private final UpdateSoftwareTokenPinReqHandler updateSoftwareTokenPinReqHandler;
    private final InitSoftwareTokenReqHandler initSoftwareTokenReqHandler;
    private final GetTokenInfoReqHandler getTokenInfoReqHandler;
    private final GetTokenInfoForKeyIdReqHandler getTokenInfoForKeyIdReqHandler;
    private final GetTokenBatchSigningEnabledReqHandler getTokenBatchSigningEnabledReqHandler;
    private final GetTokenInfoAndKeyIdForCertHashReqHandler getTokenInfoAndKeyIdForCertHashReqHandler;
    private final GetTokenInfoAndKeyIdForCertRequestIdReqHandler getTokenInfoAndKeyIdForCertRequestIdReqHandler;
    private final GetHSMOperationalInfoReqHandler getHSMOperationalInfoReqHandler;
    private final GetMemberSigningInfoReqHandler getMemberSigningInfoReqHandler;
    private final SetTokenFriendlyNameReqHandler setTokenFriendlyNameReqHandler;
    private final ListTokensReqHandler listTokensReqHandler;

    @Override
    public void listTokens(Empty request, StreamObserver<ListTokensResp> responseObserver) {
        listTokensReqHandler.processSingle(request, responseObserver);
    }

    @Override
    public void activateToken(ActivateTokenReq request, StreamObserver<Empty> responseObserver) {
        activateTokenReqHandler.processSingle(request, responseObserver);
    }

    @Override
    public void getTokenById(GetTokenByIdReq request, StreamObserver<TokenInfoProto> responseObserver) {
        getTokenInfoReqHandler.processSingle(request, responseObserver);
    }

    @Override
    public void getTokenByKey(GetTokenByKeyIdReq request, StreamObserver<TokenInfoProto> responseObserver) {
        getTokenInfoForKeyIdReqHandler.processSingle(request, responseObserver);
    }

    @Override
    public void getTokenAndKeyIdByCertRequestId(GetTokenByCertRequestIdReq request,
                                                StreamObserver<TokenInfoAndKeyIdProto> responseObserver) {
        getTokenInfoAndKeyIdForCertRequestIdReqHandler.processSingle(request, responseObserver);
    }

    @Override
    public void getTokenAndKeyIdByCertHash(GetTokenByCertHashReq request, StreamObserver<TokenInfoAndKeyIdProto> responseObserver) {
        getTokenInfoAndKeyIdForCertHashReqHandler.processSingle(request, responseObserver);
    }

    @Override
    public void setTokenFriendlyName(SetTokenFriendlyNameReq request, StreamObserver<Empty> responseObserver) {
        setTokenFriendlyNameReqHandler.processSingle(request, responseObserver);
    }

    @Override
    public void getTokenBatchSigningEnabled(GetTokenBatchSigningEnabledReq request,
                                            StreamObserver<GetTokenBatchSigningEnabledResp> responseObserver) {
        getTokenBatchSigningEnabledReqHandler.processSingle(request, responseObserver);
    }

    @Override
    public void getHSMOperationalInfo(Empty request, StreamObserver<GetHSMOperationalInfoResp> responseObserver) {
        getHSMOperationalInfoReqHandler.processSingle(request, responseObserver);
    }

    @Override
    public void initSoftwareToken(InitSoftwareTokenReq request, StreamObserver<Empty> responseObserver) {
        initSoftwareTokenReqHandler.processSingle(request, responseObserver);
    }

    @Override
    public void updateSoftwareTokenPin(UpdateSoftwareTokenPinReq request, StreamObserver<Empty> responseObserver) {
        updateSoftwareTokenPinReqHandler.processSingle(request, responseObserver);
    }

    @Override
    public void getMemberSigningInfo(GetMemberSigningInfoReq request, StreamObserver<GetMemberSigningInfoResp> responseObserver) {
        getMemberSigningInfoReqHandler.processSingle(request, responseObserver);
    }
}
