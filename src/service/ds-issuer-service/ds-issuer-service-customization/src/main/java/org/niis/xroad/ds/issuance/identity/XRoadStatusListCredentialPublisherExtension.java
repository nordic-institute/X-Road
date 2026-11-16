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
package org.niis.xroad.ds.issuance.identity;

import org.eclipse.edc.identityhub.spi.verifiablecredentials.store.CredentialStore;
import org.eclipse.edc.issuerservice.spi.credentials.statuslist.StatusListCredentialPublisher;
import org.eclipse.edc.runtime.metamodel.annotation.Configuration;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.runtime.metamodel.annotation.Settings;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.PortMapping;
import org.eclipse.edc.web.spi.configuration.PortMappingRegistry;

import static org.eclipse.edc.spi.constants.CoreConstants.JSON_LD;
import static org.niis.xroad.ds.issuance.identity.XRoadStatusListCredentialPublisherExtension.NAME;

/**
 * Registers {@link XRoadStatusListCredentialPublisher} as the {@link StatusListCredentialPublisher}, and
 * mounts {@link XRoadStatusListCredentialController} on the {@code statuslist} port and path EDC's own {@code
 * LocalStatusListCredentialPublisherExtension} would have used. That upstream extension must stay excluded
 * ({@code xroad.edc.boot.excluded-service-extensions}): its {@code initialize()} reads the EDC {@code
 * Hostname} eagerly to build its fixed callback base URL, which throws until the issuer identity is bound.
 */
@Extension(NAME)
public class XRoadStatusListCredentialPublisherExtension implements ServiceExtension {

    static final String NAME = "X-Road Issuer StatusList Publisher";

    private static final String STATUS_LIST = "statuslist";

    @Inject
    private BoundIssuerIdentityHolder holder;

    @Inject
    private CredentialStore store;

    @Inject
    private PortMappingRegistry portMappingRegistry;

    @Inject
    private WebService webServer;

    @Inject
    private Monitor monitor;

    @Inject
    private TypeManager typeManager;

    @Configuration
    private StatusListCredentialEndpointConfig config;

    public XRoadStatusListCredentialPublisherExtension() {
    }

    XRoadStatusListCredentialPublisherExtension(BoundIssuerIdentityHolder holder) {
        this.holder = holder;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        portMappingRegistry.register(new PortMapping(STATUS_LIST, config.port(), config.path()));
        var controller = new XRoadStatusListCredentialController(store, monitor, () -> typeManager.getMapper(JSON_LD), holder);
        webServer.registerResource(STATUS_LIST, controller);
    }

    @Provider
    public StatusListCredentialPublisher statusListCredentialPublisher() {
        return new XRoadStatusListCredentialPublisher(holder);
    }

    @Settings
    record StatusListCredentialEndpointConfig(
            @Setting(key = "web.http.statuslist.port", defaultValue = "9999", description = "Port of the statuslist endpoint")
            int port,
            @Setting(key = "web.http.statuslist.path", defaultValue = "/statuslist", description = "Path of the statuslist endpoint")
            String path) {
    }
}
