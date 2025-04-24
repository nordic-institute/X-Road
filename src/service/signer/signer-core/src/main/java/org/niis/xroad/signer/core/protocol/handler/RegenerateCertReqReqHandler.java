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

import ee.ria.xroad.common.CodedException;

import com.google.protobuf.ByteString;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.niis.xroad.common.rpc.mapper.ClientIdMapper;
import org.niis.xroad.signer.api.dto.CertRequestInfo;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.api.dto.TokenInfoAndKeyId;
import org.niis.xroad.signer.core.tokenmanager.TokenManager;
import org.niis.xroad.signer.core.tokenmanager.token.SoftwareTokenType;
import org.niis.xroad.signer.core.util.TokenAndKey;
import org.niis.xroad.signer.proto.RegenerateCertRequestReq;
import org.niis.xroad.signer.proto.RegenerateCertRequestResp;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;

import static ee.ria.xroad.common.ErrorCodes.X_CSR_NOT_FOUND;
import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static java.util.Optional.ofNullable;
import static org.niis.xroad.signer.core.util.ExceptionHelper.keyNotAvailable;

/**
 * Handles certificate request re-generations.
 */
@Slf4j
@ApplicationScoped
public class RegenerateCertReqReqHandler extends AbstractGenerateCertReq<RegenerateCertRequestReq, RegenerateCertRequestResp> {

    @Override
    protected RegenerateCertRequestResp handle(RegenerateCertRequestReq message) throws Exception {
        TokenAndKey tokenAndKey = findTokenAndKeyForCsrId(message.getCertRequestId());

        if (!TokenManager.isKeyAvailable(tokenAndKey.getKeyId())) {
            throw keyNotAvailable(tokenAndKey.getKeyId());
        }

        if (tokenAndKey.key().getUsage() == KeyUsageInfo.AUTHENTICATION
                && !SoftwareTokenType.ID.equals(tokenAndKey.tokenId())) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Authentication keys are only supported for software tokens");
        }

        String csrId = message.getCertRequestId();

        CertRequestInfo certRequestInfo = TokenManager.getCertRequestInfo(csrId);
        if (certRequestInfo == null) {
            throw CodedException.tr(X_CSR_NOT_FOUND,
                    "csr_not_found", "Certificate request '%s' not found", csrId);
        }

        String subjectName = certRequestInfo.getSubjectName();
        String subjectAltName = certRequestInfo.getSubjectAltName();

        PKCS10CertificationRequest generatedRequest = buildSignedCertRequest(tokenAndKey, subjectName, subjectAltName,
                tokenAndKey.key().getUsage());

        final RegenerateCertRequestResp.Builder builder = RegenerateCertRequestResp.newBuilder()
                .setCertReqId(message.getCertRequestId())
                .setCertRequest(ByteString.copyFrom(convert(generatedRequest, message.getFormat())))
                .setFormat(message.getFormat())
                .setKeyUsage(tokenAndKey.key().getUsage());
        ofNullable(certRequestInfo.getMemberId()).map(ClientIdMapper::toDto).ifPresent(builder::setMemberId);
        return builder.build();
    }

    private TokenAndKey findTokenAndKeyForCsrId(String certRequestId) {
        TokenInfoAndKeyId tokenInfoAndKeyId = TokenManager.findTokenAndKeyIdForCertRequestId(certRequestId);
        KeyInfo keyInfo = TokenManager.getKeyInfo(tokenInfoAndKeyId.getKeyId());
        return new TokenAndKey(tokenInfoAndKeyId.getTokenInfo().getId(),
                keyInfo);
    }

}
