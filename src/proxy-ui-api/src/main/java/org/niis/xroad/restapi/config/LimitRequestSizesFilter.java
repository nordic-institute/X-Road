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

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ResponseStatus;

import javax.servlet.DispatcherType;
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
import java.util.EnumSet;

/**
 * Limit request sizes to correct values
 */
@Component
@Slf4j
public class LimitRequestSizesFilter {

    @ResponseStatus(value = HttpStatus.PAYLOAD_TOO_LARGE)
    public static class SizeLimitExceededException extends RuntimeException {
        public SizeLimitExceededException(String s) {
            super(s);
        }
    }

    @Bean
    public FilterRegistrationBean<JannenFilter> basicRequestFilter() {
        FilterRegistrationBean<JannenFilter> bean = new FilterRegistrationBean<>();
        bean.setName("RegularLimitSizeFilter");
        bean.setFilter(new JannenFilter(200, "tls-certificates"));
        bean.setDispatcherTypes(EnumSet.allOf(DispatcherType.class));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<JannenFilter> fileUploadFilter() {
        FilterRegistrationBean<JannenFilter> bean = new FilterRegistrationBean<>();
        bean.setName("BinaryUploadLimitSizeFilter");
        bean.setFilter(new JannenFilter(10000, false, "tls-certificates"));
        bean.setDispatcherTypes(EnumSet.allOf(DispatcherType.class));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }


    /**
     * Following the example of Spring's ContentCachingRequestWrapper.
     * Probably does not limit multipart-uploads properly, use
     * web container specific properties for that
     */
    public static class SizeLimitingHttpServletRequestWrapper extends HttpServletRequestWrapper {

        private ServletInputStream inputStream;
        private BufferedReader reader;

        private final int maxBytes;
        public SizeLimitingHttpServletRequestWrapper(HttpServletRequest request, int maxBytes) {
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

    private static class SizeLimitingServletInputStream extends ServletInputStream {
        private final ServletInputStream is;
        private final int maxBytes;
        private int readSoFar = 0;
        SizeLimitingServletInputStream(ServletInputStream is, int maxBytes) {
            this.is = is;
            this.maxBytes = maxBytes;
        }

        private void addReadBytesCount(int number) {
            readSoFar = readSoFar + number;
            if (readSoFar > maxBytes) {
                throw new SizeLimitExceededException("request limit " + maxBytes + " exceeded");
            }
        }
        private void addReadBytesCount() {
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

    public static class JannenFilter implements Filter {
        private final int maxBytes;
        private String[] excludedPathInfoEndings;
        private boolean exclude;

        public JannenFilter(int maxBytes, String...excludedPathInfoEndings) {
            this.maxBytes = maxBytes;
            this.excludedPathInfoEndings = excludedPathInfoEndings;
            this.exclude = true;
        }

        public JannenFilter(int maxBytes, boolean exclude, String...excludedPathInfoEndings) {
            this.maxBytes = maxBytes;
            this.excludedPathInfoEndings = excludedPathInfoEndings;
            this.exclude = exclude;
        }
        @Override
        public void doFilter(ServletRequest request, ServletResponse response,
                FilterChain chain) throws IOException, ServletException {

            boolean filter = exclude;
            boolean change = !filter;
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            String uri = httpRequest.getRequestURI();
            for (String pathInfoEnding: excludedPathInfoEndings) {
                if (uri != null && uri.endsWith(pathInfoEnding)) {
                    filter = change;
                }
            }

            ServletRequest possiblyWrapped = request;
            if (filter) {
                possiblyWrapped = new SizeLimitingHttpServletRequestWrapper(
                        (HttpServletRequest) request, maxBytes);
            }

            chain.doFilter(possiblyWrapped, response);
        }
    }
}
