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
package org.niis.xroad.proxy.core.serverproxy;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.proxy.core.addon.metaservice.serverproxy.MetadataServiceHandlerImpl;
import org.niis.xroad.proxy.core.addon.metaservice.serverproxy.RestMetadataServiceHandlerImpl;
import org.niis.xroad.proxy.core.addon.opmonitoring.serverproxy.OpMonitoringServiceHandlerImpl;
import org.niis.xroad.proxy.core.addon.proxymonitor.serverproxy.ProxyMonitorServiceHandlerImpl;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Registry/router that holds pre-built ordered lists of CDI singleton service handlers.
 * Handler lists are built once at {@link PostConstruct} startup and returned as immutable lists.
 * No handler instances are created per-request.
 */
@ApplicationScoped
@RequiredArgsConstructor
public class ServiceHandlerLoader {

    // Feature-flag deps for handler filtering and opMonitorHttpClient lifecycle:
    private final ProxyProperties proxyProperties;

    // CDI-injected handler singletons:
    private final MetadataServiceHandlerImpl metadataServiceHandler;
    private final OpMonitoringServiceHandlerImpl opMonitoringServiceHandler;
    private final ProxyMonitorServiceHandlerImpl proxyMonitorServiceHandler;
    private final DefaultServiceHandlerImpl defaultServiceHandler;
    private final RestMetadataServiceHandlerImpl restMetadataServiceHandler;
    private final DefaultRestServiceHandlerImpl defaultRestServiceHandler;

    // Pre-built ordered handler lists (immutable after init):
    private List<ServiceHandler> soapHandlers;
    private List<RestServiceHandler> restHandlers;

    @PostConstruct
    public void init() {
        buildSoapHandlerList();
        buildRestHandlerList();
    }

    private void buildSoapHandlerList() {
        var handlers = new ArrayList<ServiceHandler>();
        if (proxyProperties.addon().metaservices().enabled()) {
            handlers.add(metadataServiceHandler);
        }
        if (proxyProperties.addon().opMonitor().enabled()) {
            handlers.add(opMonitoringServiceHandler);
        }
        if (proxyProperties.addon().proxyMonitor().enabled()) {
            handlers.add(proxyMonitorServiceHandler);
        }
        handlers.add(defaultServiceHandler);  // always last — canHandle() always returns true
        soapHandlers = List.copyOf(handlers);
    }

    private void buildRestHandlerList() {
        var handlers = new ArrayList<RestServiceHandler>();
        if (proxyProperties.addon().metaservices().enabled()) {
            handlers.add(restMetadataServiceHandler);
        }
        handlers.add(defaultRestServiceHandler);  // always last — canHandle() always returns true
        restHandlers = List.copyOf(handlers);
    }

    /**
     * Returns the pre-built ordered list of SOAP service handlers.
     * The default handler is always last.
     */
    List<ServiceHandler> getSoapHandlers() {
        return soapHandlers;
    }

    /**
     * Returns the pre-built ordered list of REST service handlers.
     * The default handler is always last.
     */
    List<RestServiceHandler> getRestHandlers() {
        return restHandlers;
    }

}
