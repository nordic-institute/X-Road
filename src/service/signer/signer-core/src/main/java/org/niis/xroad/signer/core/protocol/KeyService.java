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
package org.niis.xroad.signer.core.protocol;

import io.grpc.stub.StreamObserver;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.rpc.common.Empty;
import org.niis.xroad.signer.core.protocol.handler.DeleteKeyReqHandler;
import org.niis.xroad.signer.core.protocol.handler.GenerateKeyReqHandler;
import org.niis.xroad.signer.core.protocol.handler.GetAuthKeyReqHandler;
import org.niis.xroad.signer.core.protocol.handler.GetKeyIdForCertHashReqHandler;
import org.niis.xroad.signer.core.protocol.handler.GetSignMechanismReqHandler;
import org.niis.xroad.signer.core.protocol.handler.SetKeyFriendlyNameReqHandler;
import org.niis.xroad.signer.proto.AuthKeyProto;
import org.niis.xroad.signer.proto.DeleteKeyReq;
import org.niis.xroad.signer.proto.GenerateKeyReq;
import org.niis.xroad.signer.proto.GetAuthKeyReq;
import org.niis.xroad.signer.proto.GetKeyIdForCertHashReq;
import org.niis.xroad.signer.proto.GetKeyIdForCertHashResp;
import org.niis.xroad.signer.proto.GetSignMechanismReq;
import org.niis.xroad.signer.proto.GetSignMechanismResp;
import org.niis.xroad.signer.proto.KeyServiceGrpc;
import org.niis.xroad.signer.proto.SetKeyFriendlyNameReq;
import org.niis.xroad.signer.protocol.dto.KeyInfoProto;

/**
 * Token Key gRPC service.
 */
@ApplicationScoped
@RequiredArgsConstructor
public class KeyService extends KeyServiceGrpc.KeyServiceImplBase {

    private final GetSignMechanismReqHandler getSignMechanismReqHandler;
    private final GetKeyIdForCertHashReqHandler getKeyIdForCertHashReqHandler;
    private final GenerateKeyReqHandler generateKeyReqHandler;
    private final SetKeyFriendlyNameReqHandler setKeyFriendlyNameReqHandler;
    private final DeleteKeyReqHandler deleteKeyReqHandler;
    private final GetAuthKeyReqHandler getAuthKeyReqHandler;

    @Override
    public void getKeyIdForCertHash(GetKeyIdForCertHashReq request, StreamObserver<GetKeyIdForCertHashResp> responseObserver) {
        getKeyIdForCertHashReqHandler.processSingle(request, responseObserver);
    }

    @Override
    public void setKeyFriendlyName(SetKeyFriendlyNameReq request, StreamObserver<org.niis.xroad.rpc.common.Empty> responseObserver) {
        setKeyFriendlyNameReqHandler.processSingle(request, responseObserver);
    }

    @Override
    public void getSignMechanism(GetSignMechanismReq request, StreamObserver<GetSignMechanismResp> responseObserver) {
        getSignMechanismReqHandler.processSingle(request, responseObserver);
    }

    @Override
    public void deleteKey(DeleteKeyReq request, StreamObserver<Empty> responseObserver) {
        deleteKeyReqHandler.processSingle(request, responseObserver);
    }

    @Override
    public void generateKey(GenerateKeyReq request, StreamObserver<KeyInfoProto> responseObserver) {
        generateKeyReqHandler.processSingle(request, responseObserver);
    }

    @Override
    public void getAuthKey(GetAuthKeyReq request, StreamObserver<AuthKeyProto> responseObserver) {
        getAuthKeyReqHandler.processSingle(request, responseObserver);
    }
}
