package ee.ria.xroad.signer.protocol.handler;

import ee.ria.xroad.signer.protocol.message.DeleteCertRequest;

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
