package ee.cyber.sdsb.signer.core.token;

import lombok.Value;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.util.PasswordStore;
import ee.cyber.sdsb.signer.core.TokenManager;
import ee.cyber.sdsb.signer.protocol.dto.TokenInfo;
import ee.cyber.sdsb.signer.protocol.message.ActivateToken;
import ee.cyber.sdsb.signer.protocol.message.DeleteKey;
import ee.cyber.sdsb.signer.protocol.message.GenerateKey;
import ee.cyber.sdsb.signer.util.CalculateSignature;
import ee.cyber.sdsb.signer.util.CalculatedSignature;
import ee.cyber.sdsb.signer.util.SignerUtil;
import ee.cyber.sdsb.signer.util.UpdateableActor;

public abstract class AbstractTokenWorker extends UpdateableActor {

    private static final Logger LOG =
            LoggerFactory.getLogger(AbstractTokenWorker.class);

    protected final String tokenId;

    private final String workerId;

    AbstractTokenWorker(TokenInfo tokenInfo) {
        this.tokenId = tokenInfo.getId();
        this.workerId = SignerUtil.getWorkerId(tokenInfo);
    }

    protected boolean hasKey(String keyId) {
        return TokenManager.getKeyInfo(keyId) != null;
    }

    protected boolean isPinStored() {
        try {
            return PasswordStore.getPassword(tokenId) != null;
        } catch (Exception e) {
            LOG.error("Error when checking if token is active", e);
            return false;
        }
    }

    protected String getWorkerId() {
        return workerId;
    }

    @Override
    protected void onMessage(Object message) throws Exception {
        LOG.trace("onMessage()");

        if (message instanceof ActivateToken) {
            handleActivateToken((ActivateToken) message);
        } else if (message instanceof GenerateKey) {
            handleGenerateKey((GenerateKey) message);
        } else if (message instanceof DeleteKey) {
            handleDeleteKey((DeleteKey) message);
        } else if (message instanceof CalculateSignature) {
            handleCalculateSignature((CalculateSignature) message);
        } else {
            unhandled(message);
        }
    }

    private void handleActivateToken(ActivateToken message) throws Exception {
        try {
            activateToken(message);

            TokenManager.setTokenActive(tokenId, message.isActivate());

            sendSuccessResponse();
        } catch (Exception e) {
            LOG.error("Failed to activate token '{}': ", getWorkerId(),
                    e.getMessage());
            PasswordStore.storePassword(tokenId, null);
            TokenManager.setTokenActive(tokenId, false);
            throw e;
        }
    }

    private void handleGenerateKey(GenerateKey message) throws Exception {
        GenerateKeyResult result = generateKey(message);

        String keyId = result.getKeyId();

        LOG.debug("Generated new key with id '{}'", keyId);

        if (!hasKey(keyId)) {
            TokenManager.addKey(tokenId, keyId, result.getPublicKeyBase64());
            TokenManager.setKeyAvailable(keyId, true);
        }

        sendResponse(TokenManager.findKeyInfo(keyId));
    }

    private void handleDeleteKey(DeleteKey message) throws Exception {
        deleteKey(message.getKeyId());

        TokenManager.removeKey(message.getKeyId());

        sendSuccessResponse();
    }

    private void handleCalculateSignature(CalculateSignature signRequest)
            throws Exception {
        try {
            byte[] signature =
                    sign(signRequest.getKeyId(), signRequest.getData());
            sendResponse(new CalculatedSignature(signRequest, signature, null));
        } catch (Exception e) { // catch-log-rethrow
            LOG.error("Error while signing with key '{}': {}",
                    signRequest.getKeyId(), e);
            sendResponse(new CalculatedSignature(signRequest, null, e));
        }
    }

    // ------------------------------------------------------------------------

    protected abstract void activateToken(ActivateToken message)
            throws Exception;

    protected abstract GenerateKeyResult generateKey(GenerateKey message)
            throws Exception;

    protected abstract void deleteKey(String keyId) throws Exception;

    protected abstract byte[] sign(String keyId, byte[] data) throws Exception;

    // ------------------------------------------------------------------------

    @Value
    protected static class GenerateKeyResult {
        private final String keyId;
        private final String publicKeyBase64;
    }
}
