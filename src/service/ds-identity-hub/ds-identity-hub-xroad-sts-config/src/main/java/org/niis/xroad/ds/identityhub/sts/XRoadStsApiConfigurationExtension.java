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
package org.niis.xroad.ds.identityhub.sts;

import org.eclipse.edc.identityhub.spi.webcontext.IdentityHubApiContext;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.apiversion.ApiVersionService;
import org.eclipse.edc.web.spi.configuration.PortMapping;
import org.eclipse.edc.web.spi.configuration.PortMappingRegistry;

import java.io.IOException;

import static org.niis.xroad.ds.identityhub.sts.XRoadStsApiConfigurationExtension.EXTENSION_NAME;

/**
 * Replaces upstream {@code StsApiConfigurationExtension}: registers the STS port mapping and API version info
 * with the same settings and defaults, but does not inject the stock EDC {@code JettyService}, which X-Road's own
 * Jetty module replaces. Binding the STS context to a single network interface ({@code web.http.sts.host}) is not
 * supported.
 *
 * <p>The upstream extension must be excluded from the boot's dependency graph (see
 * {@code xroad.edc.boot.excluded-service-extensions}) so both extensions do not register the same port mapping.
 */
@Extension(EXTENSION_NAME)
public class XRoadStsApiConfigurationExtension implements ServiceExtension {

    public static final String EXTENSION_NAME = "X-Road Secure Token Service API configuration";

    static final int DEFAULT_STS_PORT = 9292;
    static final String DEFAULT_STS_PATH = "/api/sts";

    private static final String API_VERSION_JSON_FILE = "sts-api-version.json";

    @Setting(key = "web.http." + IdentityHubApiContext.STS + ".port", description = "Port for " + IdentityHubApiContext.STS
            + " api context", defaultValue = DEFAULT_STS_PORT + "")
    private int stsPort;

    @Setting(key = "web.http." + IdentityHubApiContext.STS + ".path", description = "Path for " + IdentityHubApiContext.STS
            + " api context", defaultValue = DEFAULT_STS_PATH)
    private String stsPath;

    @Inject
    private PortMappingRegistry portMappingRegistry;
    @Inject
    private ApiVersionService apiVersionService;

    @Override
    public String name() {
        return EXTENSION_NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        portMappingRegistry.register(new PortMapping(IdentityHubApiContext.STS, stsPort, stsPath));

        try (var versionContent = getClass().getClassLoader().getResourceAsStream(API_VERSION_JSON_FILE)) {
            apiVersionService.registerVersionInfo(IdentityHubApiContext.STS, versionContent);
        } catch (IOException e) {
            throw new EdcException(e);
        }
    }
}
