package org.niis.xroad.common.rpc;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;

public interface VaultKeyProvider {
    KeyManager getKeyManager();

    TrustManager getTrustManager();
}
