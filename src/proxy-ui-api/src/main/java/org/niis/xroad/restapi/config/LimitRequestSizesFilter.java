/**
 * The MIT License
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.restapi.config;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ReadListener;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;

/**
 * Servlet filter which limits request sizes to some amount of bytes
 */
public class LimitRequestSizesFilter implements Filter {
    private final long maxBytes;
    private final Collection<FileUploadEndpointsConfiguration.EndpointDefinition> endpoints;
    private final Mode mode;

    public enum Mode {
        LIMIT_ENDPOINTS,
        SKIP_ENDPOINTS
    }

    /**
     * @param maxBytes number of bytes to limit requests to
     * @param endpoints endpoints that are either filtered or skipped
     * @param mode LIMIT_ENDPOINTS = size limit specified endpoints, ignore others
     *             SKIP_ENDPOINTS = ignore specified endpoints, size limit all others
     */
    public LimitRequestSizesFilter(long maxBytes,
            Collection<FileUploadEndpointsConfiguration.EndpointDefinition> endpoints,
            Mode mode) {
        this.maxBytes = maxBytes;
        this.endpoints = endpoints;
        this.mode = mode;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain) throws IOException, ServletException {

        // default filtering setting depends on mode
        boolean filterThisRequest = false;
        if (mode == Mode.SKIP_ENDPOINTS) {
            filterThisRequest = true;
        }

        // if endpoint is one of specified endpoints, change from default filtering
        if (endpoints.stream().anyMatch(
                endpoint -> endpoint.matches((HttpServletRequest) request))) {
            filterThisRequest = !filterThisRequest;
        }

        // if we filter this request, then wrap it
        ServletRequest possiblyWrapped = request;
        if (filterThisRequest) {
            possiblyWrapped = new SizeLimitingHttpServletRequestWrapper(
                    (HttpServletRequest) request, maxBytes);
        }

        chain.doFilter(possiblyWrapped, response);
    }

    /**
     * Wrapper which limits request sizes to certains number of bytes. Attempt to read more
     * bytes than that results in {@link SizeLimitExceededException}
     * Implementation follows the example of Spring's ContentCachingRequestWrapper.
     * Probably does not limit multipart-uploads properly, use
     * web container specific properties for that
     */
    private static class SizeLimitingHttpServletRequestWrapper extends HttpServletRequestWrapper {

        private ServletInputStream inputStream;
        private BufferedReader reader;

        private final long maxBytes;
        SizeLimitingHttpServletRequestWrapper(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (this.inputStream == null) {
                this.inputStream = new SizeLimitingServletInputStream(getRequest().getInputStream(), maxBytes);
            }
            return this.inputStream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            if (this.reader == null) {
                this.reader = new BufferedReader(new InputStreamReader(getInputStream(), getCharacterEncoding()));
            }
            return this.reader;
        }
    }

    /**
     * Stream that limits reading to certain number of bytes.
     * Throws {@link SizeLimitExceededException} if maximum is exceeded.
     */
    private static class SizeLimitingServletInputStream extends ServletInputStream {
        private final ServletInputStream is;
        private final long maxBytes;
        private long readSoFar = 0;
        SizeLimitingServletInputStream(ServletInputStream is, long maxBytes) {
            this.is = is;
            this.maxBytes = maxBytes;
        }

        private void addReadBytesCount(long number) throws SizeLimitExceededException {
            readSoFar = readSoFar + number;
            if (readSoFar > maxBytes) {
                throw new SizeLimitExceededException("request limit " + maxBytes + " exceeded");
            }
        }
        private void addReadBytesCount() throws SizeLimitExceededException {
            addReadBytesCount(1);
        }

        @Override
        public int read() throws IOException {
            addReadBytesCount();
            return this.is.read();
        }

        @Override
        public int read(byte[] b) throws IOException {
            int count = this.is.read(b);
            addReadBytesCount(count);
            return count;
        }

        @Override
        public int read(final byte[] b, final int off, final int len) throws IOException {
            int count = this.is.read(b, off, len);
            addReadBytesCount(count);
            return count;
        }

        @Override
        public int readLine(final byte[] b, final int off, final int len) throws IOException {
            int count = this.is.readLine(b, off, len);
            addReadBytesCount(count);
            return count;
        }

        @Override
        public boolean isFinished() {
            return this.is.isFinished();
        }

        @Override
        public boolean isReady() {
            return this.is.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            this.is.setReadListener(readListener);
        }


    }
}
