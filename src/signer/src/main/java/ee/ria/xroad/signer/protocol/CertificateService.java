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

import ee.ria.xroad.signer.protocol.handler.ActivateCertRequestHandler;
import ee.ria.xroad.signer.protocol.handler.DeleteCertReqHandler;
import ee.ria.xroad.signer.protocol.handler.DeleteCertRequestReqHandler;
import ee.ria.xroad.signer.protocol.handler.GenerateSelfSignedCertRequestHandler;
import ee.ria.xroad.signer.protocol.handler.GetCertificateInfoForHashRequestHandler;
import ee.ria.xroad.signer.protocol.handler.GetMemberCertsRequestHandler;
import ee.ria.xroad.signer.protocol.handler.ImportCertReqHandler;
import ee.ria.xroad.signer.protocol.handler.SetCertStatusRequestHandler;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.signer.proto.ActivateCertReq;
import org.niis.xroad.signer.proto.CertificateServiceGrpc;
import org.niis.xroad.signer.proto.DeleteCertReq;
import org.niis.xroad.signer.proto.DeleteCertRequestReq;
import org.niis.xroad.signer.proto.GenerateSelfSignedCertReq;
import org.niis.xroad.signer.proto.GenerateSelfSignedCertResp;
import org.niis.xroad.signer.proto.GetCertificateInfoForHashRequest;
import org.niis.xroad.signer.proto.GetCertificateInfoResponse;
import org.niis.xroad.signer.proto.GetMemberCertsRequest;
import org.niis.xroad.signer.proto.GetMemberCertsResponse;
import org.niis.xroad.signer.proto.ImportCertReq;
import org.niis.xroad.signer.proto.ImportCertResp;
import org.niis.xroad.signer.proto.RegenerateCertReqRequest;
import org.niis.xroad.signer.proto.RegenerateCertReqResponse;
import org.niis.xroad.signer.proto.SetCertStatusRequest;
import org.niis.xroad.signer.protocol.dto.Empty;
import org.springframework.stereotype.Service;

/**
 * Certificate gRPC service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CertificateService extends CertificateServiceGrpc.CertificateServiceImplBase {
    private final ActivateCertRequestHandler activateCertRequestHandler;
    private final GetCertificateInfoForHashRequestHandler getCertificateInfoForHashRequestHandler;
    private final GetMemberCertsRequestHandler getMemberCertsRequestHandler;
    private final SetCertStatusRequestHandler setCertStatusRequestHandler;
    private final DeleteCertReqHandler deleteCertReqHandler;
    private final DeleteCertRequestReqHandler deleteCertRequestReqHandler;
    private final ImportCertReqHandler importCertReqHandler;
    private final GenerateSelfSignedCertRequestHandler generateSelfSignedCertRequestHandler;

    @Override
    public void activateCert(ActivateCertReq request, StreamObserver<Empty> responseObserver) {
        activateCertRequestHandler.processSingle(request, responseObserver);
    }

    @Override
    public void getCertificateInfoForHash(GetCertificateInfoForHashRequest request,
                                          StreamObserver<GetCertificateInfoResponse> responseObserver) {
        getCertificateInfoForHashRequestHandler.processSingle(request, responseObserver);
    }

    @Override
    public void setCertStatus(SetCertStatusRequest request, StreamObserver<Empty> responseObserver) {
        setCertStatusRequestHandler.processSingle(request, responseObserver);
    }

    @Override
    public void getMemberCerts(GetMemberCertsRequest request, StreamObserver<GetMemberCertsResponse> responseObserver) {
        getMemberCertsRequestHandler.processSingle(request, responseObserver);
    }

    @Override
    public void deleteCert(DeleteCertReq request, StreamObserver<Empty> responseObserver) {
        deleteCertReqHandler.processSingle(request, responseObserver);
    }

    @Override
    public void deleteCertRequest(DeleteCertRequestReq request, StreamObserver<Empty> responseObserver) {
        deleteCertRequestReqHandler.processSingle(request, responseObserver);
    }

    @Override
    public void importCert(ImportCertReq request, StreamObserver<ImportCertResp> responseObserver) {
        importCertReqHandler.processSingle(request, responseObserver);
    }

    @Override
    public void generateSelfSignedCert(GenerateSelfSignedCertReq request, StreamObserver<GenerateSelfSignedCertResp> responseObserver) {
        generateSelfSignedCertRequestHandler.processSingle(request, responseObserver);
    }

    @Override
    public void regenerateCertReq(RegenerateCertReqRequest request, StreamObserver<RegenerateCertReqResponse> responseObserver) {
        //TODO
    }
}
