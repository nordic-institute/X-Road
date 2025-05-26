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
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.niis.xroad.common.rpc.mapper.ClientIdMapper;
import org.niis.xroad.signer.core.tokenmanager.TokenManager;
import org.niis.xroad.signer.core.tokenmanager.token.SoftwareTokenType;
import org.niis.xroad.signer.core.util.TokenAndKey;
import org.niis.xroad.signer.proto.GenerateCertRequestReq;
import org.niis.xroad.signer.proto.GenerateCertRequestResp;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;
import org.springframework.stereotype.Component;

import static ee.ria.xroad.common.ErrorCodes.X_WRONG_CERT_USAGE;
import static org.niis.xroad.signer.core.util.ExceptionHelper.keyNotAvailable;

/**
 * Handles certificate request generations.
 */
@Slf4j
@Component
public class GenerateCertReqReqHandler extends AbstractGenerateCertReq<GenerateCertRequestReq, GenerateCertRequestResp> {

    @Override
    protected GenerateCertRequestResp handle(GenerateCertRequestReq request) throws Exception {
        TokenAndKey tokenAndKey = TokenManager.findTokenAndKey(request.getKeyId());

        if (!TokenManager.isKeyAvailable(tokenAndKey.getKeyId())) {
            throw keyNotAvailable(tokenAndKey.getKeyId());
        }

        if (request.getKeyUsage() == KeyUsageInfo.AUTHENTICATION
                && !SoftwareTokenType.ID.equals(tokenAndKey.tokenId())) {
            throw CodedException.tr(X_WRONG_CERT_USAGE,
                    "auth_cert_under_softtoken",
                    "Authentication certificate requests can only be created under software tokens");
        }

        PKCS10CertificationRequest generatedRequest = buildSignedCertRequest(tokenAndKey, request.getSubjectName(),
                request.getSubjectAltName(), request.getKeyUsage());

        String certReqId = TokenManager.addCertRequest(tokenAndKey.getKeyId(),
                request.hasMemberId() ? ClientIdMapper.fromDto(request.getMemberId()) : null,
                request.getSubjectName(), request.getSubjectAltName(), request.getKeyUsage(),
                request.getCertificateProfile());

        return GenerateCertRequestResp.newBuilder()
                .setCertReqId(certReqId)
                .setCertRequest(ByteString.copyFrom(convert(generatedRequest, request.getFormat())))
                .setFormat(request.getFormat())
                .build();
    }

}
