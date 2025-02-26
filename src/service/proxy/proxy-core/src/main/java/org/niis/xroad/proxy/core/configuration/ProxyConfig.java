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
package org.niis.xroad.proxy.core.configuration;

import ee.ria.xroad.common.db.DatabaseCtx;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.impl.cert.CertHelper;
import org.niis.xroad.keyconf.KeyConfProvider;
import org.niis.xroad.keyconf.impl.CachingKeyConfImpl;
import org.niis.xroad.opmonitor.api.AbstractOpMonitoringBuffer;
import org.niis.xroad.opmonitor.api.OpMonitorCommonProperties;
import org.niis.xroad.proxy.core.ProxyProperties;
import org.niis.xroad.proxy.core.addon.opmonitoring.OpMonitoringBuffer;
import org.niis.xroad.proxy.core.opmonitoring.NullOpMonitoringBuffer;
import org.niis.xroad.proxy.core.opmonitoring.OpMonitoring;
import org.niis.xroad.serverconf.ServerConfProperties;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.serverconf.impl.ServerConfFactory;
import org.niis.xroad.signer.client.SignerRpcClient;

import static org.niis.xroad.serverconf.impl.ServerConfDatabaseConfig.SERVER_CONF_DB_CTX;

@Slf4j
public class ProxyConfig {

    @ApplicationScoped
    CertHelper certHelper(GlobalConfProvider globalConfProvider) {
        return new CertHelper(globalConfProvider);
    }

    @ApplicationScoped
    @Startup
    AbstractOpMonitoringBuffer opMonitoringBuffer(ProxyProperties.ProxyAddonProperties addonProperties,
                                                  OpMonitorCommonProperties opMonitorCommonProperties,
                                                  ServerConfProvider serverConfProvider) throws Exception {
        AbstractOpMonitoringBuffer opMonitorBuffer;
        if (addonProperties.opMonitor().enabled()) {
            log.debug("Initializing op-monitoring addon: OpMonitoringBuffer");
            opMonitorBuffer = new OpMonitoringBuffer(serverConfProvider, opMonitorCommonProperties);
        } else {
            log.debug("Initializing NullOpMonitoringBuffer");
            opMonitorBuffer = new NullOpMonitoringBuffer(serverConfProvider);
        }

        return OpMonitoring.init(opMonitorBuffer);
    }

    @ApplicationScoped
    KeyConfProvider keyConfProvider(GlobalConfProvider globalConfProvider, ServerConfProvider serverConfProvider,
                                    SignerRpcClient signerRpcClient) {
        return new CachingKeyConfImpl(globalConfProvider, serverConfProvider, signerRpcClient);
    }

    @ApplicationScoped
    ServerConfProvider serverConfProvider(@Named(SERVER_CONF_DB_CTX) DatabaseCtx databaseCtx,
                                          ServerConfProperties serverConfProperties,
                                          GlobalConfProvider globalConfProvider) {
        return ServerConfFactory.create(databaseCtx, globalConfProvider, serverConfProperties);
    }

}
