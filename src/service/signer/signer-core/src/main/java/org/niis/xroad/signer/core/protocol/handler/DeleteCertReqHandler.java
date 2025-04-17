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

import jakarta.enterprise.context.ApplicationScoped;
import org.niis.xroad.rpc.common.Empty;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.core.protocol.AbstractRpcHandler;
import org.niis.xroad.signer.proto.DeleteCertReq;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static org.niis.xroad.signer.core.util.ExceptionHelper.certWithIdNotFound;

/**
 * Handles certificate deletions. If certificate is not saved in configuration,
 * we delete it on the token. Otherwise we remove the certificate from the
 * configuration.
 */
@ApplicationScoped
public class DeleteCertReqHandler
        extends AbstractRpcHandler<DeleteCertReq, Empty> {

    @Override
    protected Empty handle(DeleteCertReq request) throws Exception {
        CertificateInfo certInfo = tokenManager.getCertificateInfo(request.getCertId());
        if (certInfo == null) {
            throw certWithIdNotFound(request.getCertId());
        }

        if (!certInfo.isSavedToConfiguration()) {
            deleteCertOnToken(request);
            return Empty.getDefaultInstance();
        } else if (tokenManager.removeCert(request.getCertId())) {
            return Empty.getDefaultInstance();
        }

        throw new CodedException(X_INTERNAL_ERROR, "Failed to delete certificate");
    }

    protected void deleteCertOnToken(DeleteCertReq deleteCert) {
        for (TokenInfo tokenInfo : tokenManager.listTokens()) {
            for (KeyInfo keyInfo : tokenInfo.getKeyInfo()) {
                for (CertificateInfo certInfo : keyInfo.getCerts()) {
                    if (deleteCert.getCertId().equals(certInfo.getId())) {
                        getTokenWorker(tokenInfo.getId())
                                .handleDeleteCert(deleteCert.getCertId());
                        return;
                    }
                }
            }
        }
    }

}
