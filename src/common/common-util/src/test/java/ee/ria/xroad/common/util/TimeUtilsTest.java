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

package ee.ria.xroad.common.util;

import org.junit.Test;
import org.mockito.MockedStatic;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class TimeUtilsTest {

    private static final int EPOCH_SECONDS = 1696572342; // 2023-10-06T06:05:42 UTC

    private final Clock spyClock = spy(Clock.class);

    @Test
    public void offsetDateTimeNow() {
        try (MockedStatic<Clock> clockMock = mockStatic(Clock.class)) {
            clockMock.when(Clock::systemDefaultZone).thenReturn(spyClock);
            when(spyClock.getZone()).thenReturn(ZoneId.of("UTC"));
            when(spyClock.instant()).thenReturn(Instant.ofEpochSecond(EPOCH_SECONDS, 123456789));

            final OffsetDateTime now = TimeUtils.offsetDateTimeNow();

            assertEquals(123456000, now.getNano());
        }
    }

    @Test
    public void offsetDateTimeNowAtZone() {
        final ZoneId zone = ZoneOffset.UTC;
        try (MockedStatic<Clock> clockMock = mockStatic(Clock.class)) {
            clockMock.when(() -> Clock.system(zone)).thenReturn(spyClock);
            when(spyClock.getZone()).thenReturn(zone);
            when(spyClock.instant()).thenReturn(Instant.ofEpochSecond(EPOCH_SECONDS, 123456789));

            final OffsetDateTime now = TimeUtils.offsetDateTimeNow(zone);

            assertEquals(123456000, now.getNano());
        }
    }

    @Test
    public void localDateTimeNow() {
        try (MockedStatic<Clock> clockMock = mockStatic(Clock.class)) {
            clockMock.when(Clock::systemDefaultZone).thenReturn(spyClock);
            when(spyClock.getZone()).thenReturn(ZoneId.of("UTC"));
            when(spyClock.instant()).thenReturn(Instant.ofEpochSecond(EPOCH_SECONDS, 123456789));

            final LocalDateTime now = TimeUtils.localDateTimeNow();

            assertEquals(123456000, now.getNano());
        }
    }

    @Test
    public void zonedDateTimeNow() {
        final ZoneId zone = ZoneOffset.UTC;
        try (MockedStatic<Clock> clockMock = mockStatic(Clock.class)) {
            clockMock.when(() -> Clock.system(zone)).thenReturn(spyClock);
            when(spyClock.getZone()).thenReturn(zone);
            when(spyClock.instant()).thenReturn(Instant.ofEpochSecond(EPOCH_SECONDS, 123456789));

            final ZonedDateTime now = TimeUtils.zonedDateTimeNow(zone);

            assertEquals(123456000, now.getNano());
        }
    }

}
