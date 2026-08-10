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

package org.niis.xroad.ds.issuance.web;

import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.web.spi.WebServer;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.niis.xroad.edc.web.jetty.XRoadJettyService;

import static org.niis.xroad.ds.issuance.web.XRoadIssuerRequestHeaderSizeExtension.NAME;

/**
 * Raises the Jetty request header size for the issuer service's web connectors. The DCP credential
 * request carries the X-Road membership claim as a bearer token embedding the sign certificate and a
 * pinned OCSP response, which exceeds Jetty's 8 KB default; the issuer would otherwise reject the
 * request with HTTP 431. The issuer service is the only X-Road dataspace component that receives this
 * claim, so the override is scoped to the issuer rather than applied to every EDC connector. Neither
 * stock EDC's {@code JettyService} nor its owned {@link XRoadJettyService} replacement expose the
 * header size as configuration, so it is set through the connector configuration callback before the
 * connectors start.
 */
@Extension(NAME)
public class XRoadIssuerRequestHeaderSizeExtension implements ServiceExtension {

    static final String NAME = "X-Road issuer request header size";

    @Setting(key = "xroad.issuer.web.max-request-header-size",
            description = "Maximum Jetty request header size in bytes for the issuer service connectors, sized to fit "
                    + "the X-Road membership claim (sign certificate + pinned OCSP) carried as a bearer token in DCP "
                    + "credential requests.",
            defaultValue = "32768")
    private int maxRequestHeaderSize;

    @Inject
    private WebServer webServer;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        if (webServer instanceof XRoadJettyService jettyService) {
            jettyService.addConnectorConfigurationCallback(connector -> connector.getConnectionFactories().stream()
                    .filter(HttpConnectionFactory.class::isInstance)
                    .map(HttpConnectionFactory.class::cast)
                    .forEach(factory -> factory.getHttpConfiguration().setRequestHeaderSize(maxRequestHeaderSize)));
        } else {
            context.getMonitor().warning("WebServer is not an XRoadJettyService; request header size not raised");
        }
    }
}
