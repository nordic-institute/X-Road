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
package org.niis.xroad.monitor.core.configuration;

import ee.ria.xroad.common.db.DatabaseCtx;

import io.grpc.BindableService;
import io.quarkus.arc.All;
import io.quarkus.runtime.Startup;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.rpc.RpcServerProperties;
import org.niis.xroad.common.rpc.credentials.RpcCredentialsConfigurer;
import org.niis.xroad.common.rpc.server.RpcServer;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.serverconf.ServerConfProperties;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.serverconf.impl.ServerConfFactory;

import java.io.IOException;
import java.util.List;

import static org.niis.xroad.serverconf.impl.ServerConfDatabaseConfig.SERVER_CONF_DB_CTX;

@Slf4j
public class MonitorConfig {

    @ApplicationScoped
    @Startup
    RpcServer rpcServer(@All List<BindableService> services,
                        EnvMonitorServerProperties rpcServerProperties,
                        RpcCredentialsConfigurer rpcCredentialsConfigurer) throws IOException {
        log.info("Starting Monitor RPC server on port {}.", rpcServerProperties.port());
        var serverCredentials = rpcCredentialsConfigurer.createServerCredentials();
        RpcServer rpcServer = new RpcServer(rpcServerProperties.listenAddress(), rpcServerProperties.port(), serverCredentials,
                builder -> services.forEach(service -> {
                    log.info("Registering {} RPC service.", service.getClass().getSimpleName());
                    builder.addService(service);
                }));
        rpcServer.afterPropertiesSet();
        return rpcServer;
    }

    @ApplicationScoped
    ServerConfProvider serverConfProvider(@Named(SERVER_CONF_DB_CTX) DatabaseCtx databaseCtx,
                                          ServerConfProperties serverConfProperties,
                                          GlobalConfProvider globalConfProvider) {
        return ServerConfFactory.create(databaseCtx, globalConfProvider, serverConfProperties.cachePeriod());
    }

    @ConfigMapping(prefix = "xroad.env-monitor.rpc")
    public interface EnvMonitorServerProperties extends RpcServerProperties {
        @WithName("enabled")
        @WithDefault("true")
        @Override
        boolean enabled();

        @WithName("listen-address")
        @WithDefault("127.0.0.1")
        @Override
        String listenAddress();

        @WithName("port")
        @WithDefault("2552")
        @Override
        int port();
    }

}
