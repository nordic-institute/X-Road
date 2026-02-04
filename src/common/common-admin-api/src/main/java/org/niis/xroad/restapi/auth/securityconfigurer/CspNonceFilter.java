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

import ee.ria.xroad.common.util.MimeTypes;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.http.HttpHeaders;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import static org.springframework.security.web.server.header.ContentSecurityPolicyServerHttpHeadersWriter.CONTENT_SECURITY_POLICY;


public class CspNonceFilter implements Filter {
    public static final String NONCE_ATTR = "cspNonce";
    public static final String CSP_NONCE_PLACEHOLDER = "__CSP_NONCE__";
    public static final int NONCE_BYTE_LENGTH = 32;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpResp = (HttpServletResponse) response;

        String nonce = (String) request.getAttribute(NONCE_ATTR);
        if (nonce != null) {
            httpResp.setHeader(CONTENT_SECURITY_POLICY, generateCsp(nonce));
            chain.doFilter(request, response);
            return;
        }

        nonce = generateNonce();

        request.setAttribute(NONCE_ATTR, nonce);

        String csp = generateCsp(nonce);
        String accept = httpReq.getHeader(HttpHeaders.ACCEPT);
        boolean mayBeHtml = accept != null && accept.contains(MimeTypes.TEXT_HTML);
        if (!mayBeHtml) {
            httpResp.setHeader(CONTENT_SECURITY_POLICY, csp);
            chain.doFilter(request, response);
            return;
        }

        BufferingResponseWrapper wrapper = new BufferingResponseWrapper(httpResp);
        chain.doFilter(request, wrapper);
        wrapper.flushBuffer();

        httpResp.setHeader(CONTENT_SECURITY_POLICY, csp);

        writeFinalOutput(wrapper, nonce, httpResp);
    }

    private static String generateCsp(String nonce) {
        return "default-src 'none'; "
                + "style-src 'self' 'nonce-" + nonce + "'; "
                + "script-src 'self' 'nonce-" + nonce + "'; "
                + "img-src data: 'self'; "
                + "font-src data: 'self'; "
                + "connect-src 'self';";
    }

    private String generateNonce() {
        String nonce;
        byte[] nonceBytes = new byte[NONCE_BYTE_LENGTH];
        secureRandom.nextBytes(nonceBytes);
        nonce = Base64.getEncoder().encodeToString(nonceBytes);
        return nonce;
    }

    private static void writeFinalOutput(BufferingResponseWrapper wrapper, String nonce, HttpServletResponse httpResp)
            throws IOException {
        String contentType = wrapper.getContentType();
        byte[] content = wrapper.getContent();

        if (contentType != null && contentType.contains(MimeTypes.TEXT_HTML)) {
            String html = new String(content, StandardCharsets.UTF_8);
            String modified = html.replace(CSP_NONCE_PLACEHOLDER, nonce);
            byte[] modifiedBytes = modified.getBytes(StandardCharsets.UTF_8);

            httpResp.setContentLength(modifiedBytes.length);
            httpResp.getOutputStream().write(modifiedBytes);
        } else {
            httpResp.setContentLength(content.length);
            httpResp.getOutputStream().write(content);
        }
    }

    private static class BufferingResponseWrapper extends HttpServletResponseWrapper {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private ServletOutputStream outputStream;
        private PrintWriter writer;

        BufferingResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public ServletOutputStream getOutputStream() {
            if (outputStream == null) {
                outputStream = new ServletOutputStream() {
                    @Override
                    public void write(int b) {
                        buffer.write(b);
                    }

                    @Override
                    public boolean isReady() {
                        return true;

                    }

                    @Override
                    public void setWriteListener(WriteListener listener) {
                        // no-op
                    }
                };
            }
            return outputStream;
        }

        @Override
        public PrintWriter getWriter() {
            if (writer == null) {
                writer = new PrintWriter(new OutputStreamWriter(buffer, StandardCharsets.UTF_8));
            }
            return writer;
        }

        @Override
        public void flushBuffer() throws IOException {
            if (writer != null) writer.flush();
            if (outputStream != null) outputStream.flush();
        }

        public byte[] getContent() {
            return buffer.toByteArray();
        }
    }
}
