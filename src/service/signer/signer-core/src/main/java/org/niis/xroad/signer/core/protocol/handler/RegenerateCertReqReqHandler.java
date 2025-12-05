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
package org.niis.xroad.signer.core.protocol.handler;

import com.google.protobuf.ByteString;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.rpc.mapper.ClientIdMapper;
import org.niis.xroad.signer.api.dto.CertRequestInfo;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.api.dto.TokenInfoAndKeyId;
import org.niis.xroad.signer.core.protocol.handler.service.CertRequestCreationService;
import org.niis.xroad.signer.core.tokenmanager.TokenLookup;
import org.niis.xroad.signer.core.tokenmanager.token.SoftwareTokenDefinition;
import org.niis.xroad.signer.core.util.TokenAndKey;
import org.niis.xroad.signer.proto.RegenerateCertRequestReq;
import org.niis.xroad.signer.proto.RegenerateCertRequestResp;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;
import org.niis.xroad.signer.shared.protocol.AbstractRpcHandler;

import static java.util.Optional.ofNullable;
import static org.niis.xroad.signer.core.util.ExceptionHelper.csrWithIdNotFound;
import static org.niis.xroad.signer.core.util.ExceptionHelper.keyNotAvailable;

/**
 * Handles certificate request re-generations.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class RegenerateCertReqReqHandler extends AbstractRpcHandler<RegenerateCertRequestReq, RegenerateCertRequestResp> {
    private final CertRequestCreationService certRequestCreationService;
    private final TokenLookup tokenLookup;

    @Override
    protected RegenerateCertRequestResp handle(RegenerateCertRequestReq message) {
        TokenAndKey tokenAndKey = findTokenAndKeyForCsrId(message.getCertRequestId());

        if (!tokenLookup.isKeyAvailable(tokenAndKey.getKeyId())) {
            throw keyNotAvailable(tokenAndKey.getKeyId());
        }

        if (tokenAndKey.key().getUsage() == KeyUsageInfo.AUTHENTICATION
                && !SoftwareTokenDefinition.ID.equals(tokenAndKey.tokenId())) {
            throw XrdRuntimeException.systemInternalError(
                    "Authentication keys are only supported for software tokens");
        }

        String csrId = message.getCertRequestId();

        CertRequestInfo certRequestInfo = tokenLookup.getCertRequestInfo(csrId);
        if (certRequestInfo == null) {
            throw csrWithIdNotFound(csrId);
        }

        String subjectName = certRequestInfo.getSubjectName();
        String subjectAltName = certRequestInfo.getSubjectAltName();

        try {
            PKCS10CertificationRequest generatedRequest = certRequestCreationService.buildSignedCertRequest(tokenAndKey, subjectName,
                    subjectAltName, tokenAndKey.key().getUsage());

            final RegenerateCertRequestResp.Builder builder = RegenerateCertRequestResp.newBuilder()
                    .setCertReqId(message.getCertRequestId())
                    .setCertRequest(ByteString.copyFrom(certRequestCreationService.convert(generatedRequest, message.getFormat())))
                    .setFormat(message.getFormat())
                    .setKeyUsage(tokenAndKey.key().getUsage());
            ofNullable(certRequestInfo.getMemberId()).map(ClientIdMapper::toDto).ifPresent(builder::setMemberId);
            return builder.build();
        } catch (Exception e) {
            throw XrdRuntimeException.systemException(e);
        }
    }

    private TokenAndKey findTokenAndKeyForCsrId(String certRequestId) {
        TokenInfoAndKeyId tokenInfoAndKeyId = tokenLookup.findTokenAndKeyIdForCertRequestId(certRequestId);
        KeyInfo keyInfo = tokenLookup.getKeyInfo(tokenInfoAndKeyId.getKeyId());
        return new TokenAndKey(tokenInfoAndKeyId.getTokenInfo().getId(),
                keyInfo);
    }

}
