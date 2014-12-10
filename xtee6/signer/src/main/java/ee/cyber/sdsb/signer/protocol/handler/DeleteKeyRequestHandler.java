package ee.cyber.sdsb.signer.protocol.handler;

import lombok.extern.slf4j.Slf4j;

import ee.cyber.sdsb.signer.protocol.dto.KeyInfo;
import ee.cyber.sdsb.signer.protocol.message.DeleteKey;
import ee.cyber.sdsb.signer.tokenmanager.TokenManager;
import ee.cyber.sdsb.signer.util.TokenAndKey;

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

        // If the key is saved to configuration, delete all certs and
        // cert requests from configuration. Otherwise, delete the key from
        // the module along with certs.
        if (isSavedToConfiguration(tokenAndKey.getKey())) {
            log.trace("Key '{}' is saved to configuration, "
                    + "deleting key from configuration",
                    tokenAndKey.getKey().getId());
            removeCertsFromKey(tokenAndKey.getKey());
            return success();
        } else {
            log.trace("Key '{}' is not saved to configuration, "
                    + "deleting key from module",
                    tokenAndKey.getKey().getId());
            deleteKeyFile(tokenAndKey.getTokenId(), message);
            return nothing();
        }
    }

    private static boolean isSavedToConfiguration(KeyInfo keyInfo) {
        if (!keyInfo.getCertRequests().isEmpty()) {
            return true;
        }

        return keyInfo.getCerts().stream()
                .filter(c -> c.isSavedToConfiguration())
                .findFirst().isPresent();
    }

    private static void removeCertsFromKey(KeyInfo keyInfo) throws Exception {
        keyInfo.getCerts().stream().filter(c -> c.isSavedToConfiguration())
            .forEach(certInfo -> {
                TokenManager.removeCert(certInfo.getId());
            });

        keyInfo.getCertRequests().stream().forEach(certReqInfo -> {
            TokenManager.removeCertRequest(certReqInfo.getId());
        });
    }
}
