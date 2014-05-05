package ee.cyber.sdsb.signer.protocol.handler;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.signer.core.TokenManager;
import ee.cyber.sdsb.signer.protocol.message.DeleteCert;

import static ee.cyber.sdsb.common.ErrorCodes.X_INTERNAL_ERROR;

public class DeleteCertRequestHandler
        extends AbstractDeleteFromKeyInfo<DeleteCert> {

    @Override
    protected Object handle(DeleteCert message) throws Exception {
        if (TokenManager.removeCert(message.getCertId())) {
            deleteKeyOnTokenIfNoCertsOrCertRequests();
            return success();
        }

        throw new CodedException(
                X_INTERNAL_ERROR, "Failed to delete certificate");
    }
}
