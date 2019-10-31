/*
 *  The MIT License
 *  Copyright (c) 2018 Estonian Information System Authority (RIA),
 *  Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 *  Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package org.niis.xroad.restapi.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.GenericFilterBean;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IpThrottlingFilter extends GenericFilterBean {

    public static final int CAPACITY = 10;
    public static final int STATUS = 429;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * 10 requests / minute
     * @return
     */
    private static Bucket standardBucket() {
        return Bucket4j.builder()
                .addLimit(Bandwidth.simple(CAPACITY, Duration.ofMinutes(1)))
                .build();
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws
            IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;
        String ip = httpRequest.getRemoteAddr();

        Bucket bucket = buckets.computeIfAbsent(ip, x -> standardBucket());

        // tryConsume returns false immediately if no tokens available with the bucket
        if (bucket.tryConsume(1)) {
            // the limit is not exceeded
            filterChain.doFilter(servletRequest, servletResponse);
        } else {
            // limit is exceeded
            HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;
            httpResponse.setContentType("text/plain");
            httpResponse.setStatus(STATUS);
            httpResponse.getWriter().append("Too many requests");
        }
    }

}
