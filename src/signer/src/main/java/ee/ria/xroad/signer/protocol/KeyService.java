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

import ee.ria.xroad.signer.protocol.handler.DeleteKeyReqHandler;
import ee.ria.xroad.signer.protocol.handler.GetKeyIdForCertHashRequestHandler;
import ee.ria.xroad.signer.protocol.handler.GetSignMechanismRequestHandler;
import ee.ria.xroad.signer.protocol.handler.SetKeyFriendlyNameRequestHandler;
import ee.ria.xroad.signer.protocol.handler.SignCertificateRequestHandler;
import ee.ria.xroad.signer.protocol.handler.SignRequestHandler;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.signer.proto.DeleteKeyReq;
import org.niis.xroad.signer.proto.GetKeyIdForCertHashRequest;
import org.niis.xroad.signer.proto.GetKeyIdForCertHashResponse;
import org.niis.xroad.signer.proto.GetSignMechanismRequest;
import org.niis.xroad.signer.proto.GetSignMechanismResponse;
import org.niis.xroad.signer.proto.KeyServiceGrpc;
import org.niis.xroad.signer.proto.SetKeyFriendlyNameRequest;
import org.niis.xroad.signer.proto.SignCertificateRequest;
import org.niis.xroad.signer.proto.SignCertificateResponse;
import org.niis.xroad.signer.proto.SignRequest;
import org.niis.xroad.signer.proto.SignResponse;
import org.niis.xroad.signer.protocol.dto.Empty;
import org.springframework.stereotype.Service;

/**
 * Token Key gRPC service.
 */
@Service
@RequiredArgsConstructor
public class KeyService extends KeyServiceGrpc.KeyServiceImplBase {
    private final SignRequestHandler signRequestHandler;
    private final SignCertificateRequestHandler signCertificateRequestHandler;
    private final GetSignMechanismRequestHandler getSignMechanismRequestHandler;
    private final GetKeyIdForCertHashRequestHandler getKeyIdForCertHashRequestHandler;
    private final SetKeyFriendlyNameRequestHandler setKeyFriendlyNameRequestHandler;
    private final DeleteKeyReqHandler deleteKeyReqHandler;

    @Override
    public void getKeyIdForCertHash(GetKeyIdForCertHashRequest request, StreamObserver<GetKeyIdForCertHashResponse> responseObserver) {
        getKeyIdForCertHashRequestHandler.processSingle(request, responseObserver);
    }

    @Override
    public void setKeyFriendlyName(SetKeyFriendlyNameRequest request, StreamObserver<Empty> responseObserver) {
        setKeyFriendlyNameRequestHandler.processSingle(request, responseObserver);
    }

    @Override
    public void getSignMechanism(GetSignMechanismRequest request, StreamObserver<GetSignMechanismResponse> responseObserver) {
        getSignMechanismRequestHandler.processSingle(request, responseObserver);
    }

    @Override
    public void sign(SignRequest request, StreamObserver<SignResponse> responseObserver) {
        signRequestHandler.processSingle(request, responseObserver);
    }

    @Override
    public void signCertificate(SignCertificateRequest request, StreamObserver<SignCertificateResponse> responseObserver) {
        signCertificateRequestHandler.processSingle(request, responseObserver);
    }

    @Override
    public void deleteKey(DeleteKeyReq request, StreamObserver<Empty> responseObserver) {
        deleteKeyReqHandler.processSingle(request, responseObserver);
    }
}
