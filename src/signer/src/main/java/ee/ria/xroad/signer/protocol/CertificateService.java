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

import ee.ria.xroad.signer.protocol.handler.ActivateCertReqHandler;
import ee.ria.xroad.signer.protocol.handler.DeleteCertReqHandler;
import ee.ria.xroad.signer.protocol.handler.DeleteCertRequestReqHandler;
import ee.ria.xroad.signer.protocol.handler.GenerateCertReqReqHandler;
import ee.ria.xroad.signer.protocol.handler.GenerateSelfSignedCertReqHandler;
import ee.ria.xroad.signer.protocol.handler.GetCertificateInfoForHashReqHandler;
import ee.ria.xroad.signer.protocol.handler.GetMemberCertsReqHandler;
import ee.ria.xroad.signer.protocol.handler.ImportCertReqHandler;
import ee.ria.xroad.signer.protocol.handler.RegenerateCertReqReqHandler;
import ee.ria.xroad.signer.protocol.handler.SetCertStatusReqHandler;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.signer.proto.ActivateCertReq;
import org.niis.xroad.signer.proto.CertificateServiceGrpc;
import org.niis.xroad.signer.proto.DeleteCertReq;
import org.niis.xroad.signer.proto.DeleteCertRequestReq;
import org.niis.xroad.signer.proto.GenerateCertRequestReq;
import org.niis.xroad.signer.proto.GenerateCertRequestResp;
import org.niis.xroad.signer.proto.GenerateSelfSignedCertReq;
import org.niis.xroad.signer.proto.GenerateSelfSignedCertResp;
import org.niis.xroad.signer.proto.GetCertificateInfoForHashReq;
import org.niis.xroad.signer.proto.GetCertificateInfoResp;
import org.niis.xroad.signer.proto.GetMemberCertsReq;
import org.niis.xroad.signer.proto.GetMemberCertsResp;
import org.niis.xroad.signer.proto.ImportCertReq;
import org.niis.xroad.signer.proto.ImportCertResp;
import org.niis.xroad.signer.proto.RegenerateCertRequestReq;
import org.niis.xroad.signer.proto.RegenerateCertRequestResp;
import org.niis.xroad.signer.proto.SetCertStatusReq;
import org.niis.xroad.signer.protocol.dto.Empty;
import org.springframework.stereotype.Service;

/**
 * Certificate gRPC service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CertificateService extends CertificateServiceGrpc.CertificateServiceImplBase {
    private final ActivateCertReqHandler activateCertReqHandler;
    private final GetCertificateInfoForHashReqHandler getCertificateInfoForHashReqHandler;
    private final GetMemberCertsReqHandler getMemberCertsReqHandler;
    private final SetCertStatusReqHandler setCertStatusReqHandler;
    private final DeleteCertReqHandler deleteCertReqHandler;
    private final DeleteCertRequestReqHandler deleteCertRequestReqHandler;
    private final ImportCertReqHandler importCertReqHandler;
    private final GenerateSelfSignedCertReqHandler generateSelfSignedCertReqHandler;
    private final RegenerateCertReqReqHandler regenerateCertReqReqHandler;
    private final GenerateCertReqReqHandler generateCertReqReqHandler;

    @Override
    public void activateCert(ActivateCertReq request, StreamObserver<Empty> responseObserver) {
        activateCertReqHandler.processSingle(request, responseObserver);
    }

    @Override
    public void getCertificateInfoForHash(GetCertificateInfoForHashReq request,
                                          StreamObserver<GetCertificateInfoResp> responseObserver) {
        getCertificateInfoForHashReqHandler.processSingle(request, responseObserver);
    }

    @Override
    public void setCertStatus(SetCertStatusReq request, StreamObserver<Empty> responseObserver) {
        setCertStatusReqHandler.processSingle(request, responseObserver);
    }

    @Override
    public void getMemberCerts(GetMemberCertsReq request, StreamObserver<GetMemberCertsResp> responseObserver) {
        getMemberCertsReqHandler.processSingle(request, responseObserver);
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
        generateSelfSignedCertReqHandler.processSingle(request, responseObserver);
    }

    @Override
    public void generateCertRequest(GenerateCertRequestReq request, StreamObserver<GenerateCertRequestResp> responseObserver) {
        generateCertReqReqHandler.processSingle(request, responseObserver);
    }

    @Override
    public void regenerateCertRequest(RegenerateCertRequestReq request, StreamObserver<RegenerateCertRequestResp> responseObserver) {
        regenerateCertReqReqHandler.processSingle(request, responseObserver);
    }
}
