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
import java.io.InputStream;
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
     * Possibly does not limit multipart/form-data uploads properly, use
     * web container specific properties (server.tomcat.max-http-post-size) for that
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
     * Size limit is "best effort", it may fail to limit reading to the exact number of
     * bytes specified, especially if
     * {@link InputStream#skip(long)}},
     * {@link InputStream#mark(int)}}, and
     * {@link InputStream#reset()} are used and underlying stream does not used the overloaded read-methods
     * (which support size counting and limiting) to implement those.
     * Not threadsafe.
     * Does not notify ReadListeners about errors due to maximum size exceeded.
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

        /**
         * Increases numberToAdd of read bytes by numerToAdd and throws exception if limit exceeded
         * @param numberToAdd number of read bytes. If -1, interpreted as "EOF" and ignored
         * @param localBefore local copy of read bytes at the start of the method which calls this
         * @throws SizeLimitExceededException if limit for read bytes was exceeded
         */
        private void increaseReadBytesCount(long localBefore, long numberToAdd) throws SizeLimitExceededException {
            if (numberToAdd != -1) {
                // we count what next counter value should be, in our opinion
                long localNextReadSoFar = localBefore + numberToAdd;
                // We do not decrease the counter.
                // Counter may have been increased by another caller.
                // For example readLine() usually calls read() and both
                // attempt to increase the counter. In this case the increase from readLine()
                // does overwrite the value from read()s, but only if it
                // results in same, or larger, counter value.
                if (localNextReadSoFar > readSoFar) {
                    readSoFar = localNextReadSoFar;
                }
                if (readSoFar > maxBytes) {
                    throw new SizeLimitExceededException("request limit " + maxBytes + " exceeded");
                }
            }
        }
        private void increaseReadBytesCount(long localBefore) throws SizeLimitExceededException {
            increaseReadBytesCount(localBefore, 1);
        }

        @Override
        public int read() throws IOException {
            long localReadSoFar = readSoFar;
            int value = this.is.read();
            if (value != -1) {
                increaseReadBytesCount(localReadSoFar);
            }
            return value;
        }

        @Override
        public int read(byte[] b) throws IOException {
            long localReadSoFar = readSoFar;
            int count = this.is.read(b);
            increaseReadBytesCount(localReadSoFar, count);
            return count;
        }

        @Override
        public int read(final byte[] b, final int off, final int len) throws IOException {
            long localReadSoFar = readSoFar;
            int count = this.is.read(b, off, len);
            increaseReadBytesCount(localReadSoFar, count);
            return count;
        }

        @Override
        public int readLine(final byte[] b, final int off, final int len) throws IOException {
            long localReadSoFar = readSoFar;
            int count = this.is.readLine(b, off, len);
            increaseReadBytesCount(localReadSoFar, count);
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

        @Override
        public long skip(long l) throws IOException {
            return this.is.skip(l);
        }

        @Override
        public int available() throws IOException {
            return this.is.available();
        }

        @Override
        public void close() throws IOException {
            this.is.close();
        }

        @Override
        public synchronized void mark(int i) {
            this.is.mark(i);
        }

        @Override
        public synchronized void reset() throws IOException {
            this.is.reset();
        }

        @Override
        public boolean markSupported() {
            return this.is.markSupported();
        }
    }
}
