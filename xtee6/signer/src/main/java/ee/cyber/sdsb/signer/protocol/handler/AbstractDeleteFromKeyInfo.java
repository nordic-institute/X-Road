package ee.cyber.sdsb.signer.protocol.handler;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.signer.core.TokenManager;
import ee.cyber.sdsb.signer.protocol.AbstractRequestHandler;
import ee.cyber.sdsb.signer.protocol.dto.KeyInfo;
import ee.cyber.sdsb.signer.protocol.dto.TokenInfo;
import ee.cyber.sdsb.signer.protocol.message.DeleteKey;

import static ee.cyber.sdsb.common.ErrorCodes.X_KEY_NOT_FOUND;

abstract class AbstractDeleteFromKeyInfo<T> extends AbstractRequestHandler<T> {

    protected void deleteKeyOnTokenIfNoCertsOrCertRequests() {
        for (TokenInfo tokenInfo : TokenManager.listTokens()) {
            for (KeyInfo keyInfo : tokenInfo.getKeyInfo()) {
                try {
                    deleteKeyIfNoCertsOrCertRequests(keyInfo.getId());
                } catch (Exception e) {
                    LOG.error("Failed to delete key '{}': {}",
                            keyInfo.getId(), e);
                }
            }
        }
    }

    protected void deleteKeyFile(String tokenId, DeleteKey message) {
        tellTokenWorker(message, tokenId);
    }

    protected Object deleteCertRequest(String certId) throws Exception {
        String keyId = TokenManager.removeCertRequest(certId);
        if (keyId != null) {
            deleteKeyIfNoCertsOrCertRequests(keyId);

            LOG.info("Deleted certificate request under key '{}'", keyId);
            return success();
        }

        throw new CodedException(X_KEY_NOT_FOUND,
                "Certificate request '%s' not found", certId);
    }

    private boolean hasCertsOrCertRequests(String keyId) throws Exception {
        KeyInfo key = TokenManager.findKeyInfo(keyId);
        return !key.getCerts().isEmpty() || !key.getCertRequests().isEmpty();
    }

    private void deleteKeyIfNoCertsOrCertRequests(String keyId)
            throws Exception {
        if (!hasCertsOrCertRequests(keyId)) {
            String tokenId = TokenManager.findTokenIdForKeyId(keyId);
            deleteKeyFile(tokenId, new DeleteKey(keyId));
            if (!TokenManager.removeKey(keyId)) {
                LOG.warn("Did not remove key '{}' although it has no " +
                        "certificates or certificate requests", keyId);
            }
        }
    }
}
