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
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.rpc.mapper.ClientIdMapper;
import org.niis.xroad.signer.core.protocol.AbstractRpcHandler;
import org.niis.xroad.signer.core.protocol.handler.service.CertRequestCreationService;
import org.niis.xroad.signer.core.tokenmanager.CertManager;
import org.niis.xroad.signer.core.tokenmanager.TokenLookup;
import org.niis.xroad.signer.core.tokenmanager.token.SoftwareTokenDefinition;
import org.niis.xroad.signer.core.util.TokenAndKey;
import org.niis.xroad.signer.proto.GenerateCertRequestReq;
import org.niis.xroad.signer.proto.GenerateCertRequestResp;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;

import static org.niis.xroad.common.core.exception.ErrorCode.WRONG_CERT_USAGE;
import static org.niis.xroad.signer.core.util.ExceptionHelper.keyNotAvailable;

/**
 * Handles certificate request generations.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class GenerateCertReqReqHandler extends AbstractRpcHandler<GenerateCertRequestReq, GenerateCertRequestResp> {
    private final CertRequestCreationService certRequestCreationService;
    private final TokenLookup tokenLookup;
    private final CertManager certManager;

    @Override
    protected GenerateCertRequestResp handle(GenerateCertRequestReq request) {
        TokenAndKey tokenAndKey = tokenLookup.findTokenAndKey(request.getKeyId());

        if (!tokenLookup.isKeyAvailable(tokenAndKey.getKeyId())) {
            throw keyNotAvailable(tokenAndKey.getKeyId());
        }

        if (request.getKeyUsage() == KeyUsageInfo.AUTHENTICATION
                && !SoftwareTokenDefinition.ID.equals(tokenAndKey.tokenId())) {
            throw XrdRuntimeException.systemException(WRONG_CERT_USAGE,
                    "Authentication certificate requests can only be created under software tokens");
        }

        try {
            var generatedRequest = certRequestCreationService.buildSignedCertRequest(tokenAndKey, request.getSubjectName(),
                    request.getSubjectAltName(), request.getKeyUsage());

            String certReqId = certManager.addCertRequest(tokenAndKey.getKeyId(),
                    request.hasMemberId() ? ClientIdMapper.fromDto(request.getMemberId()) : null,
                    request.getSubjectName(), request.getSubjectAltName(), request.getKeyUsage(),
                    request.getCertificateProfile());

            return GenerateCertRequestResp.newBuilder()
                    .setCertReqId(certReqId)
                    .setCertRequest(ByteString.copyFrom(certRequestCreationService.convert(generatedRequest, request.getFormat())))
                    .setFormat(request.getFormat())
                    .build();
        } catch (Exception e) {
            throw XrdRuntimeException.systemException(e);
        }
    }

}
