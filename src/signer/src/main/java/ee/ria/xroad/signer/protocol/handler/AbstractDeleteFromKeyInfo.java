/**
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
package ee.ria.xroad.signer.protocol.handler;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.signer.protocol.AbstractRequestHandler;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;
import ee.ria.xroad.signer.protocol.message.DeleteCert;
import ee.ria.xroad.signer.protocol.message.DeleteKey;
import ee.ria.xroad.signer.tokenmanager.TokenManager;

import lombok.extern.slf4j.Slf4j;

import static ee.ria.xroad.common.ErrorCodes.X_CSR_NOT_FOUND;

@Slf4j
abstract class AbstractDeleteFromKeyInfo<T> extends AbstractRequestHandler<T> {

    protected void deleteCertOnToken(DeleteCert deleteCert) {
        for (TokenInfo tokenInfo : TokenManager.listTokens()) {
            for (KeyInfo keyInfo : tokenInfo.getKeyInfo()) {
                for (CertificateInfo certInfo : keyInfo.getCerts()) {
                    if (deleteCert.getCertId().equals(certInfo.getId())) {
                        tellToken(deleteCert, tokenInfo.getId());
                        return;
                    }
                }
            }
        }
    }

    protected void deleteKeyFile(String tokenId, DeleteKey message) {
        tellToken(message, tokenId);
    }

    protected Object deleteCertRequest(String certId) {
        String keyId = TokenManager.removeCertRequest(certId);
        if (keyId != null) {
            log.info("Deleted certificate request under key '{}'", keyId);
            return success();
        }

        throw CodedException.tr(X_CSR_NOT_FOUND,
                "csr_not_found", "Certificate request '%s' not found", certId);
    }
}
