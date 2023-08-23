/**
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
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
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.CertificateInfoProto;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.tokenmanager.TokenManager;

import com.google.protobuf.AbstractMessage;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.signer.proto.ActivateCertRequest;
import org.niis.xroad.signer.proto.CertificateServiceGrpc;
import org.niis.xroad.signer.proto.GetCertificateInfoForHashRequest;
import org.niis.xroad.signer.proto.GetCertificateInfoResponse;
import org.niis.xroad.signer.proto.GetMemberCertsRequest;
import org.niis.xroad.signer.proto.GetMemberCertsResponse;
import org.niis.xroad.signer.proto.RegenerateCertReqRequest;
import org.niis.xroad.signer.proto.RegenerateCertReqResponse;
import org.niis.xroad.signer.proto.SetCertStatusRequest;
import org.niis.xroad.signer.protocol.dto.Empty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.ErrorCodes.X_CERT_NOT_FOUND;

/**
 * Handles requests for token list.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CertificateService extends CertificateServiceGrpc.CertificateServiceImplBase {
    private final TemporaryAkkaMessenger temporaryAkkaMessenger;

    @Override
    public void activateCert(ActivateCertRequest request, StreamObserver<Empty> responseObserver) {
        TokenManager.setCertActive(request.getCertIdOrHash(),
                request.getActive());
        emitSingleAndClose(responseObserver, Empty.getDefaultInstance());
    }

    @Override
    public void getCertificateInfoForHash(GetCertificateInfoForHashRequest request, StreamObserver<GetCertificateInfoResponse> responseObserver) {
        CertificateInfo certificateInfo = TokenManager.getCertificateInfoForCertHash(request.getCertHash());

        if (certificateInfo == null) {
            throw CodedException.tr(X_CERT_NOT_FOUND, "certificate_with_hash_not_found",
                    "Certificate with hash '%s' not found", request.getCertHash());
        }

        emitSingleAndClose(responseObserver, GetCertificateInfoResponse.newBuilder()
                .setCertificateInfo(certificateInfo.asMessage())
                .build());
    }

    @Override
    public void setCertStatus(SetCertStatusRequest request, StreamObserver<Empty> responseObserver) {
        TokenManager.setCertStatus(request.getCertId(), request.getStatus());

        emitSingleAndClose(responseObserver, Empty.getDefaultInstance());
    }

    @Override
    public void getMemberCerts(GetMemberCertsRequest request, StreamObserver<GetMemberCertsResponse> responseObserver) {
        final var memberId = ClientIdMapper.fromDto(request.getMemberId());
        List<CertificateInfoProto> memberCerts = TokenManager.listTokens().stream()
                .flatMap(t -> t.getKeyInfo().stream())
                .filter(k -> k.getUsage() == KeyUsageInfo.SIGNING)
                .flatMap(k -> k.getCerts().stream())
                .filter(c -> containsMember(c.getMemberId(), memberId))
                .map(CertificateInfo::asMessage)
                .collect(Collectors.toList());

        emitSingleAndClose(responseObserver, GetMemberCertsResponse.newBuilder()
                .addAllCerts(memberCerts)
                .build());
    }

    private static boolean containsMember(ClientId first, ClientId second) {
        if (first == null || second == null) {
            return false;
        }

        return first.equals(second) || second.subsystemContainsMember(first);
    }


    @Override
    public void regenerateCertReq(RegenerateCertReqRequest request, StreamObserver<RegenerateCertReqResponse> responseObserver) {
        super.regenerateCertReq(request, responseObserver);
    }

    private <T extends AbstractMessage> void emitSingleAndClose(StreamObserver<T> responseObserver, T value) {
        responseObserver.onNext(value);
        responseObserver.onCompleted();
    }
}
