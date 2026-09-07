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
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.common.core.exception.ErrorCode.SSL_AUTH_FAILED;

class UnusableAddressTrackerTest {

    private static final Duration PERIOD_SECONDS = Duration.ofSeconds(300);

    private static final URI ADDRESS_1 = URI.create("https://ss1.example.org:5500");
    private static final URI ADDRESS_2 = URI.create("https://ss2.example.org:5500");

    private final FakeTicker ticker = new FakeTicker();
    private final UnusableAddressTracker tracker = new UnusableAddressTracker(PERIOD_SECONDS, ticker);

    @Test
    void markedAddressIsUnusableUntilPeriodExpires() {
        tracker.markUnusable(ADDRESS_1);

        assertThat(tracker.isUnusable(ADDRESS_1)).isTrue();
        assertThat(tracker.isUnusable(ADDRESS_2)).isFalse();

        ticker.advance(PERIOD_SECONDS.getSeconds() + 1, TimeUnit.SECONDS);

        assertThat(tracker.isUnusable(ADDRESS_1)).isFalse();
    }

    @Test
    void filterUsableRemovesMarkedAddresses() {
        tracker.markUnusable(ADDRESS_1);

        assertThat(tracker.filterUsable(new URI[]{ADDRESS_1, ADDRESS_2})).containsExactly(ADDRESS_2);
    }

    @Test
    void filterUsableReturnsEmptyWhenAllAddressesAreMarked() {
        tracker.markUnusable(ADDRESS_1);
        tracker.markUnusable(ADDRESS_2);

        assertThat(tracker.filterUsable(new URI[]{ADDRESS_1, ADDRESS_2})).isEmpty();
    }

    @Test
    void disabledTrackerDoesNotMarkOrFilter() {
        var disabledTracker = new UnusableAddressTracker(Duration.ZERO, ticker);
        disabledTracker.markUnusable(ADDRESS_1);

        assertThat(disabledTracker.isUnusable(ADDRESS_1)).isFalse();
        assertThat(disabledTracker.filterUsable(new URI[]{ADDRESS_1, ADDRESS_2}))
                .containsExactly(ADDRESS_1, ADDRESS_2);
    }

    @Test
    void negativePeriodDisablesTracker() {
        var disabledTracker = new UnusableAddressTracker(Duration.ofSeconds(-1), ticker);
        disabledTracker.markUnusable(ADDRESS_1);

        assertThat(disabledTracker.isUnusable(ADDRESS_1)).isFalse();
    }

    @Test
    void detectsDirectHandshakeFailure() {
        assertThat(UnusableAddressTracker.isHandshakeFailure(new SSLHandshakeException("rejected"))).isTrue();
    }

    @Test
    void detectsWrappedHandshakeFailure() {
        var wrapped = XrdRuntimeException.systemException(SSL_AUTH_FAILED,
                new SSLHandshakeException("rejected"), "TLS handshake failed");

        assertThat(UnusableAddressTracker.isHandshakeFailure(wrapped)).isTrue();
    }

    @Test
    void detectsDeeplyNestedHandshakeFailure() {
        var nested = new IOException(new SSLException(new SSLHandshakeException("rejected")));

        assertThat(UnusableAddressTracker.isHandshakeFailure(nested)).isTrue();
    }

    @Test
    void ignoresNonHandshakeFailures() {
        assertThat(UnusableAddressTracker.isHandshakeFailure(new SSLException("not a handshake error"))).isFalse();
        assertThat(UnusableAddressTracker.isHandshakeFailure(new SocketTimeoutException("connect timed out"))).isFalse();
        assertThat(UnusableAddressTracker.isHandshakeFailure(new IOException("connection reset"))).isFalse();
        assertThat(UnusableAddressTracker.isHandshakeFailure(null)).isFalse();
    }

    private static final class FakeTicker extends Ticker {
        private long ticks;

        @Override
        public long read() {
            return ticks;
        }

        void advance(long duration, TimeUnit unit) {
            ticks += unit.toNanos(duration);
        }
    }
}
