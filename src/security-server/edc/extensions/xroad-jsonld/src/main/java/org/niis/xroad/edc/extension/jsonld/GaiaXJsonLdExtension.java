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
package org.niis.xroad.edc.extension.jsonld;

import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

import static java.lang.String.format;
import static org.niis.xroad.edc.extension.jsonld.GaiaXJsonLdExtension.NAME;

@Extension(NAME)
public class GaiaXJsonLdExtension implements ServiceExtension {
    static final String NAME = "X-Road Gaia-X JSON-LD extension";

    @Inject
    private JsonLd jsonLdService;

    @Override
    public void initialize(ServiceExtensionContext context) {
        var contextDir = "json-ld-context";
        getResourceUri(contextDir + File.separator + "trusted-shape-registry.jsonld")
                .onSuccess(uri -> jsonLdService.registerCachedDocument(XRoadVcConstants.GX_TRUSTED_SHAPE_REGISTRY_URL, uri))
                .onFailure(failure -> context.getMonitor().warning("Failed to register cached json-ld document: " + failure.getFailureDetail()));

        getResourceUri(contextDir + File.separator + "jws-2020-v1.jsonld")
                .onSuccess(uri -> jsonLdService.registerCachedDocument("https://w3id.org/security/suites/jws-2020/v1", uri))
                .onFailure(failure -> context.getMonitor().warning("Failed to register cached json-ld document: " + failure.getFailureDetail()));

        getResourceUri(contextDir + File.separator + "did-v1.jsonld")
                .onSuccess(uri -> jsonLdService.registerCachedDocument("https://www.w3.org/ns/did/v1", uri))
                .onFailure(failure -> context.getMonitor().warning("Failed to register cached json-ld document: " + failure.getFailureDetail()));
    }

    @NotNull
    private Result<URI> getResourceUri(String name) {
        var uri = getClass().getClassLoader().getResource(name);
        if (uri == null) {
            return Result.failure(format("Cannot find resource %s", name));
        }

        try {
            return Result.success(uri.toURI());
        } catch (URISyntaxException e) {
            return Result.failure(format("Cannot read resource %s: %s", name, e.getMessage()));
        }
    }
}
