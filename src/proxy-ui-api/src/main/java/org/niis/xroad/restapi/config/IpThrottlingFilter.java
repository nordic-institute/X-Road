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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.sleuth.instrument.web.TraceWebServletAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.GenericFilterBean;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Configuration
@Order(IpThrottlingFilter.IP_THROTTLING_FILTER_ORDER)
@Slf4j
/**
 * Filter which rate limits requests
 */
public class IpThrottlingFilter extends GenericFilterBean {
    public static final int IP_THROTTLING_FILTER_ORDER = AddCorrelationIdFilter.CORRELATION_ID_FILTER_ORDER + 3;

    @Value("${ratelimit.requests.per.second}")
    private int rateLimitRequestsPerSecond;

    @Value("${ratelimit.requests.per.minute}")
    private int rateLimitRequestsPerMinute;

    public static final int EXPIRE_BUCKETS_FROM_CACHE_MINUTES = 5;

    // use Guava LoadingCache to store buckets, for easy eviction of old buckets
    LoadingCache<String, Bucket> bucketCache = CacheBuilder.newBuilder()
            .expireAfterAccess(EXPIRE_BUCKETS_FROM_CACHE_MINUTES, TimeUnit.MINUTES)
            .build(new CacheLoader<String, Bucket>() {
                public Bucket load(String key) {
                    Bucket bucket = createStandardBucket();
                    return bucket;
                }
            });

    /**
     * create a new bucket
     * @return
     */
    private Bucket createStandardBucket() {
        return Bucket4j.builder()
                .addLimit(Bandwidth.simple(rateLimitRequestsPerSecond, Duration.ofSeconds(1)))
                .addLimit(Bandwidth.simple(rateLimitRequestsPerMinute, Duration.ofMinutes(1)))
                .build();
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws
            IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;
        String ip = httpRequest.getRemoteAddr();

        Bucket bucket = null;
        try {
            bucket = bucketCache.get(ip);
        } catch (ExecutionException e) {
            log.error("Could not load value from cache", e);
        }

        // tryConsume returns false immediately if no tokens available with the bucket
        if (bucket == null || bucket.tryConsume(1)) {
            // the limit is not exceeded (or we could not use buckets)
            filterChain.doFilter(servletRequest, servletResponse);
        } else {
            // limit is exceeded, respond with 429 TOO_MANY_REQUESTS
            HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;
            httpResponse.setContentType("application/json");
            httpResponse.setCharacterEncoding("UTF-8");
            httpResponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            httpResponse.getWriter().append("{\"status\":429}");
        }
    }

}
