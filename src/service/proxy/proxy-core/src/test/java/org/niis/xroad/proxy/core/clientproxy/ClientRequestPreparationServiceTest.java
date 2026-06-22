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

import ee.ria.xroad.common.util.HttpSender;

import com.google.common.base.Ticker;
import org.junit.jupiter.api.Test;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.service.ServiceAddressResolver;
import org.niis.xroad.proxy.core.util.OpMonitoringDataHelper;

import javax.net.ssl.SSLHandshakeException;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.niis.xroad.proxy.core.clientproxy.FastestConnectionSelectingSSLSocketFactory.ID_SELECTED_TARGET;
import static org.niis.xroad.proxy.core.clientproxy.FastestConnectionSelectingSSLSocketFactory.ID_TARGETS;

class ClientRequestPreparationServiceTest {

    private static final URI ADDRESS_1 = URI.create("https://ss1.example.org:5500");
    private static final URI ADDRESS_2 = URI.create("https://ss2.example.org:5500");

    private final UnusableAddressTracker tracker =
            new UnusableAddressTracker(Duration.ofSeconds(180), Ticker.systemTicker());
    private final ClientRequestPreparationService service = new ClientRequestPreparationService(
            mock(ServiceAddressResolver.class), mock(ProxyProperties.class),
            mock(OpMonitoringDataHelper.class), tracker);
    private final HttpSender httpSender = mock(HttpSender.class);

    @Test
    void marksSelectedTargetOnHandshakeFailureWithMultipleAddresses() {
        when(httpSender.getAttribute(ID_TARGETS)).thenReturn(new URI[]{ADDRESS_1, ADDRESS_2});
        when(httpSender.getAttribute(ID_SELECTED_TARGET)).thenReturn(ADDRESS_1);

        service.markAddressUnusableIfHandshakeFailure(httpSender, new SSLHandshakeException("rejected"));

        assertThat(tracker.isUnusable(ADDRESS_1)).isTrue();
        assertThat(tracker.isUnusable(ADDRESS_2)).isFalse();
    }

    @Test
    void doesNotMarkSingleAddress() {
        when(httpSender.getAttribute(ID_TARGETS)).thenReturn(new URI[]{ADDRESS_1});
        when(httpSender.getAttribute(ID_SELECTED_TARGET)).thenReturn(ADDRESS_1);

        service.markAddressUnusableIfHandshakeFailure(httpSender, new SSLHandshakeException("rejected"));

        assertThat(tracker.isUnusable(ADDRESS_1)).isFalse();
    }

    @Test
    void doesNotMarkWhenNoTargetWasSelected() {
        when(httpSender.getAttribute(ID_TARGETS)).thenReturn(new URI[]{ADDRESS_1, ADDRESS_2});
        when(httpSender.getAttribute(ID_SELECTED_TARGET)).thenReturn(null);

        service.markAddressUnusableIfHandshakeFailure(httpSender, new SSLHandshakeException("rejected"));

        assertThat(tracker.isUnusable(ADDRESS_1)).isFalse();
        assertThat(tracker.isUnusable(ADDRESS_2)).isFalse();
    }

    @Test
    void doesNotMarkOnNonHandshakeFailure() {
        when(httpSender.getAttribute(ID_TARGETS)).thenReturn(new URI[]{ADDRESS_1, ADDRESS_2});
        when(httpSender.getAttribute(ID_SELECTED_TARGET)).thenReturn(ADDRESS_1);

        service.markAddressUnusableIfHandshakeFailure(httpSender, new IOException("connection reset"));

        assertThat(tracker.isUnusable(ADDRESS_1)).isFalse();
    }
}
