package ee.cyber.sdsb.common.util;

import org.junit.Before;
import org.junit.Test;

import ee.cyber.sdsb.common.SystemProperties;

import static org.junit.Assert.assertEquals;

public class SystemMetricsBehavior {

    @Before
    public void setUp() {
        System.setProperty(
                SystemProperties.NET_STATS_FILE, "src/test/resources/dev");
    }

    @Test
    public void shouldGetNetStats() {
        // Given/when
        NetStats actualStats = SystemMetrics.getNetStats();

        // Then
        NetStats expectedStats = new NetStats(12345678, 87654321);
        assertEquals(expectedStats, actualStats);
    }
}
