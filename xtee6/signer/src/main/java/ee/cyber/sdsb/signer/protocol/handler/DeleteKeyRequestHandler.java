package ee.cyber.sdsb.signer.protocol.handler;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.signer.core.TokenManager;
import ee.cyber.sdsb.signer.protocol.dto.KeyInfo;
import ee.cyber.sdsb.signer.protocol.dto.TokenInfo;
import ee.cyber.sdsb.signer.protocol.message.DeleteKey;

import static ee.cyber.sdsb.common.ErrorCodes.X_KEY_NOT_FOUND;

public class DeleteKeyRequestHandler
        extends AbstractDeleteFromKeyInfo<DeleteKey> {

    @Override
    protected Object handle(DeleteKey message) throws Exception {
        for (TokenInfo tokenInfo : TokenManager.listTokens()) {
            for (KeyInfo keyInfo : tokenInfo.getKeyInfo()) {
                if (message.getKeyId().equals(keyInfo.getId())) {
                    if (TokenManager.removeKey(message.getKeyId())) {
                        deleteKeyFile(tokenInfo.getId(), message);
                        return success();
                    }
                }
            }
        }

        throw new CodedException(X_KEY_NOT_FOUND, "Key '%s' not found",
                message.getKeyId());
    }
}
