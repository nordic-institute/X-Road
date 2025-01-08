/*
 * The MIT License
 *
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

package ee.ria.xroad.proxy;

import ee.ria.xroad.common.cert.CertChainFactory;
import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;
import ee.ria.xroad.common.conf.serverconf.ServerConfProvider;
import ee.ria.xroad.common.db.DatabaseCtxV2;
import ee.ria.xroad.common.messagelog.AbstractLogManager;
import ee.ria.xroad.common.opmonitoring.AbstractOpMonitoringBuffer;
import ee.ria.xroad.proxy.clientproxy.AbstractClientProxyHandler;
import ee.ria.xroad.proxy.clientproxy.AsicContainerHandler;
import ee.ria.xroad.proxy.clientproxy.MetadataHandler;
import ee.ria.xroad.proxy.conf.KeyConfProvider;
import ee.ria.xroad.proxy.messagelog.LogManager;
import ee.ria.xroad.proxy.messagelog.NullLogManager;
import ee.ria.xroad.proxy.opmonitoring.NullOpMonitoringBuffer;
import ee.ria.xroad.proxy.opmonitoring.OpMonitoringBuffer;
import ee.ria.xroad.proxy.serverproxy.MetadataServiceHandlerImpl;
import ee.ria.xroad.proxy.serverproxy.OpMonitoringServiceHandlerImpl;
import ee.ria.xroad.proxy.serverproxy.ProxyMonitorServiceHandlerImpl;
import ee.ria.xroad.proxy.serverproxy.RestMetadataServiceHandlerImpl;
import ee.ria.xroad.proxy.serverproxy.RestServiceHandler;
import ee.ria.xroad.proxy.serverproxy.ServiceHandler;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Slf4j
class ProxyAddonsConfig {


    @Produces
    @Dependent
    @Priority(100)
    AbstractClientProxyHandler metadataHandler(@ConfigProperty(name = "xroad.proxy.addon.metaservices.enabled") boolean enabled,
                                               GlobalConfProvider globalConfProvider, KeyConfProvider keyConfProvider,
                                               ServerConfProvider serverConfProvider, CertChainFactory certChainFactory,
                                               @Named("proxyHttpClient") HttpClient httpClient) {
        if (enabled) {
            log.debug("Initializing metaservices addon: MetadataHandler");
            return new MetadataHandler(globalConfProvider, keyConfProvider,
                    serverConfProvider, certChainFactory, httpClient);
        }
        return null;
    }

    @Produces
    @Dependent
    ServiceHandler metadataServiceHandler(@ConfigProperty(name = "xroad.proxy.addon.metaservices.enabled") boolean enabled,
                                          ServerConfProvider serverConfProvider, GlobalConfProvider globalConfProvider) {
        if (enabled) {
            return new MetadataServiceHandlerImpl(serverConfProvider, globalConfProvider);
        }
        return null;
    }

    @Produces
    @Dependent
    RestServiceHandler restMetadataServiceHandler(@ConfigProperty(name = "xroad.proxy.addon.metaservices.enabled") boolean enabled,
                                                  ServerConfProvider serverConfProvider) {
        if (enabled) {
            return new RestMetadataServiceHandlerImpl(serverConfProvider);
        }
        return null;
    }


    @Produces
    @ApplicationScoped
    @Priority(200)
    AbstractClientProxyHandler asicContainerHandler(
            @ConfigProperty(name = "xroad.proxy.addon.messagelog.enabled") boolean enabled,
            GlobalConfProvider globalConfProvider,
            KeyConfProvider keyConfProvider,
            ServerConfProvider serverConfProvider,
            CertChainFactory certChainFactory,
            @Named("proxyHttpClient") HttpClient client,
            Instance<DatabaseCtxV2> messagelogDatabaseCtx) {

        if (!enabled) {
            log.debug("Initializing messagelog addon: AsicContainerHandler");
            return new AsicContainerHandler(
                    globalConfProvider,
                    keyConfProvider,
                    serverConfProvider,
                    certChainFactory,
                    client,
                    messagelogDatabaseCtx.isResolvable() ? messagelogDatabaseCtx.get() : null);
        }
        return null;
    }

    @Produces
    @ApplicationScoped
    AbstractLogManager logManager(
            @ConfigProperty(name = "xroad.proxy.addon.messagelog.enabled") boolean enabled,
            GlobalConfProvider globalConfProvider,
            ServerConfProvider serverConfProvider,
            Instance<DatabaseCtxV2> messagelogDatabaseCtx) {

        var dbCtx = messagelogDatabaseCtx.isResolvable()
                ? messagelogDatabaseCtx.get()
                : null;

        if (enabled) {
            log.debug("Initializing messagelog addon: LogManager");
            return new LogManager("proxy", globalConfProvider, serverConfProvider, dbCtx);
        }

        log.debug("Initializing messagelog addon: NullLogManager");
        return new NullLogManager("proxy", globalConfProvider, serverConfProvider, dbCtx);
    }


    @Produces
    @Dependent
    ServiceHandler proxyMonitorServiceHandler(
            @ConfigProperty(name = "xroad.proxy.addon.proxymonitor.enabled") boolean enabled,
            ServerConfProvider serverConfProvider, GlobalConfProvider globalConfProvider) {
        if (enabled) {
            log.debug("Initializing proxymonitoring addon: ProxyMonitorServiceHandlerImpl");
            return new ProxyMonitorServiceHandlerImpl(serverConfProvider, globalConfProvider);
        }
        return null;
    }


    @Inject
    @ConfigProperty(name = "xroad.proxy.addon.op-monitor.enabled")
    boolean opMonitorEnabled;

    @Produces
    @Dependent
    ServiceHandler opMonitoringServiceHandler(ServerConfProvider serverConfProvider, GlobalConfProvider globalConfProvider) {
        log.debug("Initializing op-monitoring addon: OpMonitoringServiceHandlerImpl");
        return new OpMonitoringServiceHandlerImpl(serverConfProvider, globalConfProvider);
    }

    @Produces
    @ApplicationScoped
    AbstractOpMonitoringBuffer opMonitoringBuffer(ServerConfProvider serverConfProvider) throws Exception {
        if (opMonitorEnabled) {
            log.debug("Initializing op-monitoring addon: OpMonitoringBuffer");
            return new OpMonitoringBuffer(serverConfProvider);
        }
        return new NullOpMonitoringBuffer(serverConfProvider);
    }


}
