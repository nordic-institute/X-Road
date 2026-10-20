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
package org.niis.xroad.edc.issuer.didresolution;

import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.properties.config.keys.DspConfigKeys;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DidResolutionExtensionTest {

    private static final String KEY = DspConfigKeys.DID_RESOLUTION_FETCH_TIMEOUT_MS.key();
    private static final long DEFAULT_MILLIS = 3000L;

    @Mock
    private ServiceExtensionContext context;

    @Mock
    private Monitor monitor;

    @Test
    void aPositiveTimeoutIsUsedAsConfigured() {
        when(context.getSetting(KEY, DEFAULT_MILLIS)).thenReturn(1500L);

        assertThat(DidResolutionExtension.fetchTimeout(context, monitor)).isEqualTo(Duration.ofMillis(1500));
        verify(monitor, never()).warning(contains(KEY));
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, -3000L})
    void aTimeoutBelowOneMillisecondFallsBackToTheDefaultWithAWarning(long configured) {
        when(context.getSetting(KEY, DEFAULT_MILLIS)).thenReturn(configured);

        assertThat(DidResolutionExtension.fetchTimeout(context, monitor)).isEqualTo(Duration.ofMillis(DEFAULT_MILLIS));
        verify(monitor).warning(contains(KEY));
    }
}
