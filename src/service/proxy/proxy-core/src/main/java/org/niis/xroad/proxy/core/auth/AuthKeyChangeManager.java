package org.niis.xroad.proxy.core.auth;

import ee.ria.xroad.common.util.filewatcher.FileWatcherRunner;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.keyconf.KeyConfProvider;
import org.niis.xroad.keyconf.impl.CachingKeyConfImpl;
import org.niis.xroad.proxy.core.clientproxy.ClientProxy;
import org.niis.xroad.proxy.core.serverproxy.ServerProxy;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

@Slf4j
public class AuthKeyChangeManager implements InitializingBean, DisposableBean {
    private final KeyConfProvider keyConfProvider;
    private final ClientProxy clientProxy;
    private final ServerProxy serverProxy;
    private FileWatcherRunner changeWatcher;

    public AuthKeyChangeManager(KeyConfProvider keyConfProvider, ClientProxy clientProxy, ServerProxy serverProxy) {
        this.keyConfProvider = keyConfProvider;
        this.clientProxy = clientProxy;
        this.serverProxy = serverProxy;
    }

    @Override
    public void afterPropertiesSet() {
        changeWatcher = CachingKeyConfImpl.createChangeWatcher(this::onAuthKeyChange);
    }

    private void onAuthKeyChange() {
        log.debug("Authentication key change detected, reloading key.");
        if (keyConfProvider instanceof CachingKeyConfImpl cachingKeyConf) {
            cachingKeyConf.invalidateCaches();
        }
        clientProxy.reloadAuthKey();
        serverProxy.reloadAuthKey();
    }

    @Override
    public void destroy() throws Exception {
        if (changeWatcher != null) {
            changeWatcher.stop();
        }
    }
}
