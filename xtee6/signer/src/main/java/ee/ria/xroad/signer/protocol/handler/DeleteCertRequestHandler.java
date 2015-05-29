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
