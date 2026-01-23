/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.globalconf.impl.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Provider;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.confclient.rpc.ConfClientRpcClient;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.GlobalConfSource;
import org.niis.xroad.globalconf.ManagedLifecycleGlobalConfSource;
import org.niis.xroad.globalconf.extension.GlobalConfExtensionFactory;
import org.niis.xroad.globalconf.extension.GlobalConfExtensions;
import org.niis.xroad.globalconf.impl.FileSystemGlobalConfSource;
import org.niis.xroad.globalconf.impl.GlobalConfImpl;
import org.niis.xroad.globalconf.impl.RemoteGlobalConfDataLoader;
import org.niis.xroad.globalconf.impl.RemoteGlobalConfSource;
import org.niis.xroad.globalconf.impl.extension.GlobalConfExtensionFactoryImpl;

import static org.niis.xroad.globalconf.impl.config.GlobalConfProperties.GlobalConfSource.REMOTE;

@Slf4j
public class GlobalConfConfig {

    @ApplicationScoped
    public GlobalConfProvider globalConfProvider(GlobalConfSource source) {
        return new GlobalConfImpl(source, globalConfExtensions(source));
    }

    @ApplicationScoped
    public GlobalConfSource globalConfSource(Provider<ConfClientRpcClient> confClientRpcClientProvider,
                                             GlobalConfProperties globalConfProperties) {
        ManagedLifecycleGlobalConfSource globalConfSource;
        if (globalConfProperties.source() == REMOTE) {
            var globalConfClient = confClientRpcClientProvider.get();
            if (globalConfClient == null) {
                throw new IllegalStateException("GlobalConf remoting is enabled, but globalConfClient is not available");
            } else {
                log.info("GlobalConf source is set to: RemoteGlobalConfSource(gRPC)");
                globalConfSource = new RemoteGlobalConfSource(
                        globalConfClient,
                        remoteGlobalConfDataLoader());
            }
        } else {
            log.info("GlobalConf source is set to: VersionedConfigurationDirectory(FS)");
            globalConfSource = new FileSystemGlobalConfSource(globalConfProperties.configurationPath());
        }

        globalConfSource.init();
        return globalConfSource;
    }

    private GlobalConfExtensions globalConfExtensions(GlobalConfSource source) {
        return new GlobalConfExtensions(source, globalConfExtensionFactory());
    }

    private GlobalConfExtensionFactory globalConfExtensionFactory() {
        return new GlobalConfExtensionFactoryImpl();
    }

    private RemoteGlobalConfDataLoader remoteGlobalConfDataLoader() {
        return new RemoteGlobalConfDataLoader();
    }

}
