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
package org.niis.xroad.proxy.core.rpc;

import io.grpc.BindableService;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.rpc.RpcServerProperties;
import org.niis.xroad.common.rpc.credentials.RpcCredentialsConfigurer;
import org.niis.xroad.common.rpc.server.ManagedRpcServer;
import org.niis.xroad.proxy.core.addon.proxymonitor.util.ProxyMonitorService;
import org.niis.xroad.proxy.core.admin.AdminService;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.tls.InternalTlsService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Startup
@Singleton
public class ProxyRpcServer extends ManagedRpcServer {

    public ProxyRpcServer(RpcServerProperties rpcServerProperties,
                          RpcCredentialsConfigurer rpcCredentialsConfigurer,
                          ProxyProperties.Addon addonProperties,
                          AdminService adminService,
                          InternalTlsService internalTlsService) {
        super(getServices(addonProperties, adminService, internalTlsService), rpcServerProperties, rpcCredentialsConfigurer);
    }

    @Override
    @PostConstruct
    public void init() throws IOException {
        super.init();
    }

    @Override
    @PreDestroy
    public void destroy() throws InterruptedException {
        super.destroy();
    }

    private static List<BindableService> getServices(ProxyProperties.Addon addonProperties,
                                                     AdminService adminService,
                                                     InternalTlsService internalTlsService) {
        List<BindableService> rpcServices = new ArrayList<>();
        rpcServices.add(adminService);
        rpcServices.add(internalTlsService);

        if (addonProperties.proxyMonitor().enabled()) {
            rpcServices.add(new ProxyMonitorService());
        }
        return rpcServices;
    }
}
