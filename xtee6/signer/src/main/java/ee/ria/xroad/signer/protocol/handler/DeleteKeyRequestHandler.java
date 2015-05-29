package ee.ria.xroad.signer.protocol.handler;

import lombok.extern.slf4j.Slf4j;

import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.message.DeleteKey;
import ee.ria.xroad.signer.tokenmanager.TokenManager;
import ee.ria.xroad.signer.util.TokenAndKey;

/**
 * Handles key deletions.
 */
@Slf4j
public class DeleteKeyRequestHandler
        extends AbstractDeleteFromKeyInfo<DeleteKey> {

    @Override
    protected Object handle(DeleteKey message) throws Exception {
        TokenAndKey tokenAndKey =
                TokenManager.findTokenAndKey(message.getKeyId());

        if (message.isDeleteFromDevice()) {
            log.trace("Deleting key '{}' from device", message.getKeyId());

            deleteKeyFile(tokenAndKey.getTokenId(), message);
            return nothing();
        } else {
            log.trace("Deleting key '{}' from configuration",
                    message.getKeyId());

            removeCertsFromKey(tokenAndKey.getKey());
            return success();
        }
    }

    private static void removeCertsFromKey(KeyInfo keyInfo) throws Exception {
        keyInfo.getCerts().stream().filter(c -> c.isSavedToConfiguration())
            .map(c -> c.getId()).forEach(TokenManager::removeCert);

        keyInfo.getCertRequests().stream()
            .map(c -> c.getId()).forEach(TokenManager::removeCertRequest);
    }
}
