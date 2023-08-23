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
import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.signer.protocol.dto.Empty;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.message.Sign;
import ee.ria.xroad.signer.protocol.message.SignCertificate;
import ee.ria.xroad.signer.tokenmanager.TokenManager;

import com.google.protobuf.AbstractMessage;
import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
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

import java.security.PublicKey;

import static ee.ria.xroad.common.ErrorCodes.X_CERT_NOT_FOUND;
import static ee.ria.xroad.signer.tokenmanager.TokenManager.findTokenIdForKeyId;

/**
 * Handles requests for token list.
 */
@Slf4j
@RequiredArgsConstructor
public class KeyService extends KeyServiceGrpc.KeyServiceImplBase {

    private final TemporaryAkkaMessenger temporaryAkkaMessenger;

    @Override
    public void getKeyIdForCertHash(GetKeyIdForCertHashRequest request, StreamObserver<GetKeyIdForCertHashResponse> responseObserver) {
        KeyInfo keyInfo = TokenManager.getKeyInfoForCertHash(request.getCertHash());

        if (keyInfo == null) {
            throw CodedException.tr(X_CERT_NOT_FOUND, "certificate_with_hash_not_found",
                    "Certificate with hash '%s' not found", request.getCertHash());
        }

        emitSingleAndClose(responseObserver, GetKeyIdForCertHashResponse.newBuilder()
                .setKeyId(keyInfo.getId())
                .setSignMechanismName(keyInfo.getSignMechanismName())
                .build());
    }

    @Override
    public void setKeyFriendlyName(SetKeyFriendlyNameRequest request, StreamObserver<Empty> responseObserver) {
        TokenManager.setKeyFriendlyName(request.getKeyId(),
                request.getFriendlyName());
        emitSingleAndClose(responseObserver, Empty.getDefaultInstance());
    }


    @Override
    public void getSignMechanism(GetSignMechanismRequest request, StreamObserver<GetSignMechanismResponse> responseObserver) {
        KeyInfo keyInfo = TokenManager.getKeyInfo(request.getKeyId());

        if (keyInfo == null) {
            throw CodedException.tr(ErrorCodes.X_KEY_NOT_FOUND, "key_not_found", "Key '%s' not found",
                    request.getKeyId());
        }

        emitSingleAndClose(responseObserver, GetSignMechanismResponse.newBuilder()
                .setSignMechanismName(keyInfo.getSignMechanismName())
                .build());
    }

    @Override
    public void sign(SignRequest request, StreamObserver<SignResponse> responseObserver) {
        var message = new Sign(request.getKeyId(),
                request.getSignatureAlgorithmId(),
                request.getDigest().toByteArray());

        ee.ria.xroad.signer.protocol.message.SignResponse response = temporaryAkkaMessenger
                .tellTokenWithResponse(message, findTokenIdForKeyId(message.getKeyId()));

        emitSingleAndClose(responseObserver, SignResponse.newBuilder()
                .setSignature(ByteString.copyFrom(response.getSignature()))
                .build());
    }

    @SneakyThrows //TODO:grpc handle it
    @Override
    public void signCertificate(SignCertificateRequest request, StreamObserver<SignCertificateResponse> responseObserver) {
        PublicKey publicKey = CryptoUtils.readX509PublicKey(request.getPublicKey().toByteArray());
        var message = new SignCertificate(request.getKeyId(),
                request.getSignatureAlgorithmId(),
                request.getSubjectName(),
                publicKey);

        ee.ria.xroad.signer.protocol.message.SignCertificateResponse response = temporaryAkkaMessenger
                .tellTokenWithResponse(message, findTokenIdForKeyId(message.getKeyId()));

        emitSingleAndClose(responseObserver, SignCertificateResponse.newBuilder()
                .setCertificateChain(ByteString.copyFrom(response.getCertificateChain()))
                .build());
    }


    private <T extends AbstractMessage> void emitSingleAndClose(StreamObserver<T> responseObserver, T value) {
        responseObserver.onNext(value);
        responseObserver.onCompleted();
    }
}
