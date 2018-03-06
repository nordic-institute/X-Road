/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.message.DeleteCert;
import ee.ria.xroad.signer.tokenmanager.TokenManager;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.signer.util.ExceptionHelper.certWithIdNotFound;

/**
 * Handles certificate deletions. If certificate is not saved in configuration,
 * we delete it on the token. Otherwise we remove the certificate from the
 * configuration.
 */
public class DeleteCertRequestHandler
        extends AbstractDeleteFromKeyInfo<DeleteCert> {

    @Override
    protected Object handle(DeleteCert message) throws Exception {
        CertificateInfo certInfo =
                TokenManager.getCertificateInfo(message.getCertId());
        if (certInfo == null) {
            throw certWithIdNotFound(message.getCertId());
        }

        if (!certInfo.isSavedToConfiguration()) {
            deleteCertOnToken(message);
            return success();
        } else if (TokenManager.removeCert(message.getCertId())) {
            return success();
        }

        throw new CodedException(X_INTERNAL_ERROR,
                "Failed to delete certificate");
    }
}
