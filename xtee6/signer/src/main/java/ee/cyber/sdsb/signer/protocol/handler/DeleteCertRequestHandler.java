package ee.cyber.sdsb.signer.protocol.handler;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.signer.protocol.dto.CertificateInfo;
import ee.cyber.sdsb.signer.protocol.message.DeleteCert;
import ee.cyber.sdsb.signer.tokenmanager.TokenManager;

import static ee.cyber.sdsb.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.cyber.sdsb.signer.util.ExceptionHelper.certWithIdNotFound;

public class DeleteCertRequestHandler
        extends AbstractDeleteFromKeyInfo<DeleteCert> {

    @Override
    protected Object handle(DeleteCert message) throws Exception {
        CertificateInfo certInfo =
                TokenManager.getCertificateInfo(message.getCertId());
        if (certInfo == null) {
            throw certWithIdNotFound(message.getCertId());
        }

        // If certificate is not saved in configuration, we delete it on the
        // token. Otherwise we remove the cert from the configuration.
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
