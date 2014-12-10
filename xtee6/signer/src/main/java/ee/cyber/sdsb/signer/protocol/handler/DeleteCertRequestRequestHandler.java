package ee.cyber.sdsb.signer.protocol.handler;

import ee.cyber.sdsb.signer.protocol.message.DeleteCertRequest;

/**
 * Handles certificate request deletions.
 */
public class DeleteCertRequestRequestHandler
        extends AbstractDeleteFromKeyInfo<DeleteCertRequest> {

    @Override
    protected Object handle(DeleteCertRequest message) throws Exception {
        return deleteCertRequest(message.getCertId());
    }

}
