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
import ee.ria.xroad.common.util.CryptoUtils;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.rpc.common.Empty;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.core.certmanager.OcspResponseManager;
import org.niis.xroad.signer.core.protocol.AbstractRpcHandler;
import org.niis.xroad.signer.proto.ActivateCertReq;

import java.security.cert.X509Certificate;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.util.CertUtils.isSelfSigned;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.niis.xroad.signer.core.util.ExceptionHelper.certWithIdNotFound;

/**
 * Handles certificate activations and deactivations.
 */
@Slf4j
@RequiredArgsConstructor
@ApplicationScoped
public class ActivateCertReqHandler
        extends AbstractRpcHandler<ActivateCertReq, Empty> {

    private final OcspResponseManager ocspResponseManager;

    @Override
    protected Empty handle(ActivateCertReq request) throws Exception {
        if (request.getActive()) {
            CertificateInfo certificateInfo = tokenManager.getCertificateInfo(request.getCertIdOrHash());
            if (certificateInfo == null) {
                throw certWithIdNotFound(request.getCertIdOrHash());
            }
            X509Certificate x509Certificate = CryptoUtils.readCertificate(certificateInfo.getCertificateBytes());
            if (!isSelfSigned(x509Certificate)) {
                try {
                    ocspResponseManager.verifyOcspResponses(x509Certificate);
                    if (isNotBlank(certificateInfo.getOcspVerifyBeforeActivationError())) {
                        tokenManager.setOcspVerifyBeforeActivationError(certificateInfo.getId(), "");
                    }
                } catch (Exception e) {
                    log.error("Failed to verify OCSP responses for certificate {}", certificateInfo.getCertificateDisplayName(), e);
                    tokenManager.setOcspVerifyBeforeActivationError(certificateInfo.getId(), e.getMessage());
                    throw new CodedException(X_INTERNAL_ERROR,
                            "Failed to verify OCSP responses for certificate. Error: %s",
                            e.getMessage());
                }
            }
        }

        tokenManager.setCertActive(request.getCertIdOrHash(), request.getActive());

        return Empty.getDefaultInstance();
    }
}
