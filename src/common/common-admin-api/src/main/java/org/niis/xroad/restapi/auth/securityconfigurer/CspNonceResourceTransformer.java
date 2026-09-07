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
package org.niis.xroad.restapi.auth.securityconfigurer;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.ResourceTransformer;
import org.springframework.web.servlet.resource.ResourceTransformerChain;
import org.springframework.web.servlet.resource.TransformedResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * A {@link ResourceTransformer} that replaces
 * {@value CspNonceFilter#CSP_NONCE_PLACEHOLDER} placeholders in static resources (typically
 * {@code index.html}) with the per-request CSP nonce set by {@link CspNonceFilter}.
 *
 * <p>The nonce is read from the request attribute {@value CspNonceFilter#NONCE_ATTR}.
 * If the attribute is absent (fail-safe), the resource is returned unchanged.
 */
public class CspNonceResourceTransformer implements ResourceTransformer {

    @Override
    public Resource transform(HttpServletRequest request, Resource resource,
                              ResourceTransformerChain transformerChain) throws IOException {
        var transformed = transformerChain.transform(request, resource);
        var nonce = (String) request.getAttribute(CspNonceFilter.NONCE_ATTR);
        if (nonce == null) {
            return transformed;
        }
        var html = new String(transformed.getContentAsByteArray(), StandardCharsets.UTF_8);
        var patched = html.replace(CspNonceFilter.CSP_NONCE_PLACEHOLDER, nonce);
        return new TransformedResource(transformed, patched.getBytes(StandardCharsets.UTF_8));
    }
}
