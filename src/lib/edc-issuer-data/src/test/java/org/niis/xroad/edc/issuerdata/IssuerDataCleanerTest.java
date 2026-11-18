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

package org.niis.xroad.edc.issuerdata;

import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.StoreResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IssuerDataCleanerTest {

    private static final Instant START = Instant.parse("2026-11-18T10:00:00Z");

    private final IssuerDataStore store = mock();
    private final Monitor monitor = mock();
    private final MutableClock clock = new MutableClock(START);
    private final IssuerDataCleaner cleaner = new IssuerDataCleaner(store, clock, monitor);

    @Test
    void shouldDeleteRowsBeforeTheCurrentTimeAndLogTheCountAtInfo() {
        when(store.deleteExpired(START)).thenReturn(StoreResult.success(3));

        cleaner.run();

        verify(store).deleteExpired(START);
        verify(monitor).info(contains("3"));
    }

    @Test
    void shouldStaySilentWhenNothingWasRemoved() {
        when(store.deleteExpired(START)).thenReturn(StoreResult.success(0));

        cleaner.run();

        verify(monitor, never()).info(contains("expired"));
    }

    @Test
    void shouldWarnWithoutThrowingWhenTheDeleteFailsAndTryAgainAtTheNextRun() {
        when(store.deleteExpired(START)).thenReturn(StoreResult.generalError("database is down"));
        cleaner.run();
        verify(monitor).warning(contains("database is down"));

        var later = START.plusSeconds(600);
        clock.set(later);
        when(store.deleteExpired(later)).thenReturn(StoreResult.success(1));
        cleaner.run();

        verify(store).deleteExpired(later);
        verify(monitor).info(contains("1"));
    }
}
