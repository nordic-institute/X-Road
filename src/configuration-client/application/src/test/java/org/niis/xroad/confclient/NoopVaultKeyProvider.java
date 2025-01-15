package org.niis.xroad.confclient;

import io.quarkus.test.Mock;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.rpc.VaultKeyProvider;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;

@Slf4j
@Mock
@ApplicationScoped
public class NoopVaultKeyProvider implements VaultKeyProvider {

    @PostConstruct
    public void init() {
        log.info("NoopVaultKeyProvider init");
    }

    @Override
    public KeyManager getKeyManager() {
        return null;
    }

    @Override
    public TrustManager getTrustManager() {
        return null;
    }
}
