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

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.proxy.core.ProxyProperties;
import org.niis.xroad.proxy.core.addon.metaservice.serverproxy.MetadataServiceHandlerImpl;
import org.niis.xroad.proxy.core.addon.metaservice.serverproxy.RestMetadataServiceHandlerImpl;
import org.niis.xroad.proxy.core.addon.opmonitoring.serverproxy.OpMonitoringServiceHandlerImpl;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.util.ArrayList;
import java.util.Collection;

@ApplicationScoped
@RequiredArgsConstructor
public class ServiceHandlerLoader {
    private final ServerConfProvider serverConfProvider;
    private final GlobalConfProvider globalConfProvider;
    private final ProxyProperties.ProxyAddonProperties addonProperties;


    public Collection<ServiceHandler> loadSoapServiceHandlers() {
        Collection<ServiceHandler> handlers = new ArrayList<>();
        if (addonProperties.metaservices().enabled()) {
            handlers.add(new MetadataServiceHandlerImpl(serverConfProvider, globalConfProvider));
        }
        if (addonProperties.opMonitor().enabled()) {
            handlers.add(new OpMonitoringServiceHandlerImpl(serverConfProvider, globalConfProvider));
        }
        return handlers;
    }

    public Collection<RestServiceHandler> loadRestServiceHandlers() {
        Collection<RestServiceHandler> handlers = new ArrayList<>();
        if (addonProperties.metaservices().enabled()) {
            handlers.add(new RestMetadataServiceHandlerImpl(serverConfProvider));
        }
        return handlers;
    }

}
