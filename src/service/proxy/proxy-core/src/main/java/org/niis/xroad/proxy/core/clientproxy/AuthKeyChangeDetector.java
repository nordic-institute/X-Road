package org.niis.xroad.proxy.core.clientproxy;

import org.niis.xroad.keyconf.KeyConfProvider;
import org.niis.xroad.keyconf.dto.AuthKey;

class AuthKeyChangeDetector {
    private final KeyConfProvider keyConfProvider;
    private volatile AuthKey lastAuthKey;

    AuthKeyChangeDetector(KeyConfProvider keyConfProvider) {
        this.keyConfProvider = keyConfProvider;
    }

    boolean hasAuthKeyChanged() {
        AuthKey authKey = keyConfProvider.getAuthKey();
        boolean hasChanged = lastAuthKey != null && !lastAuthKey.equals(authKey);
        lastAuthKey = authKey;
        return hasChanged;
    }
}
