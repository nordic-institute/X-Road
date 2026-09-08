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
package org.niis.xroad.restapi.auth.securityconfigurer;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;

import static org.springframework.security.web.server.header.ContentSecurityPolicyServerHttpHeadersWriter.CONTENT_SECURITY_POLICY;

/**
 * Generates a per-request CSP nonce, stores it as a request attribute ({@value #NONCE_ATTR})
 * for downstream consumers (e.g. {@link CspNonceResourceTransformer}), and sets the
 * Content-Security-Policy response header.
 */
public class CspNonceFilter extends OncePerRequestFilter {
    public static final String NONCE_ATTR = "cspNonce";
    public static final String CSP_NONCE_PLACEHOLDER = "__CSP_NONCE__";
    public static final int NONCE_BYTE_LENGTH = 32;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse httpResp, FilterChain chain)
            throws IOException, ServletException {
        var nonce = generateNonce();
        request.setAttribute(NONCE_ATTR, nonce);
        httpResp.setHeader(CONTENT_SECURITY_POLICY, generateCsp(nonce));
        chain.doFilter(request, httpResp);
    }

    private static String generateCsp(String nonce) {
        return "default-src 'none'; "
                + "style-src 'self' 'nonce-" + nonce + "'; "
                + "script-src 'self' 'nonce-" + nonce + "'; "
                + "img-src data: 'self'; "
                + "font-src data: 'self'; "
                + "connect-src 'self'; "
                + "frame-ancestors 'none'; "
                + "form-action 'self'; ";
    }

    private String generateNonce() {
        var nonceBytes = new byte[NONCE_BYTE_LENGTH];
        secureRandom.nextBytes(nonceBytes);
        return Base64.getEncoder().encodeToString(nonceBytes);
    }
}
