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
package org.niis.xroad.proxy.core.clientproxy;

import com.google.common.base.Ticker;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;

import javax.net.ssl.SSLHandshakeException;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;

/**
 * Tracks server proxy addresses that recently failed the TLS handshake so that
 * {@link FastestConnectionSelectingSSLSocketFactory} can exclude them from selection
 * for a configurable cooldown period.
 * <p>
 * The cooldown is configured with {@code xroad.proxy.client-proxy.fastest-connecting-ssl-uri-unusable-period}
 * (seconds); a non-positive value disables tracking entirely.
 */
@Slf4j
@ApplicationScoped
public class UnusableAddressTracker {

    private static final int MAXIMUM_SIZE = 10000;

    private final Cache<URI, Boolean> unusableAddresses;
    private final boolean enabled;

    @Inject
    public UnusableAddressTracker(ProxyProperties proxyProperties) {
        this(proxyProperties.clientProxy().clientProxyFastestConnectingSslUriUnusablePeriod(), Ticker.systemTicker());
    }

    UnusableAddressTracker(Duration period, Ticker ticker) {
        this.enabled = period.getSeconds() > 0;
        this.unusableAddresses = CacheBuilder.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(Math.max(period.getSeconds(), 0)))
                .maximumSize(MAXIMUM_SIZE)
                .ticker(ticker)
                .build();
    }

    /**
     * Marks the given address as unusable for the configured cooldown period.
     * No-op when tracking is disabled.
     *
     * @param address the server proxy address that failed the TLS handshake
     */
    public void markUnusable(URI address) {
        if (enabled) {
            log.warn("Marking address '{}' as unusable", address);
            unusableAddresses.put(address, Boolean.TRUE);
        }
    }

    /**
     * Tells whether the given address is currently within the unusable cooldown period.
     *
     * @param address the address to check
     * @return true if the address is marked unusable, false otherwise (always false when disabled)
     */
    public boolean isUnusable(URI address) {
        return enabled && unusableAddresses.getIfPresent(address) != null;
    }

    /**
     * Filters out addresses that are currently marked unusable.
     *
     * @param addresses candidate addresses
     * @return the usable subset (may be empty); the input array when tracking is disabled
     */
    public URI[] filterUsable(URI[] addresses) {
        if (!enabled) {
            return addresses;
        }
        return Arrays.stream(addresses)
                .filter(address -> !isUnusable(address))
                .toArray(URI[]::new);
    }

    /**
     * Tells whether the given exception was caused by a TLS handshake failure,
     * i.e. has an {@link SSLHandshakeException} in its cause chain.
     *
     * @param exception the exception to inspect
     * @return true if an SSLHandshakeException is found in the cause chain
     */
    public static boolean isHandshakeFailure(Throwable exception) {
        return ExceptionUtils.indexOfType(exception, SSLHandshakeException.class) != -1;
    }
}
