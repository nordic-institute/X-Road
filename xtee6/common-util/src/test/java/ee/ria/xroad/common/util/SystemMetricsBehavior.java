package ee.ria.xroad.common.util;

import org.junit.Before;
import org.junit.Test;

import ee.ria.xroad.common.SystemProperties;

import static org.junit.Assert.assertEquals;

/**
 * Tests to verify correct system metrics behavior.
 */
public class SystemMetricsBehavior {

    /**
     * Set up test file location.
     */
    @Before
    public void setUp() {
        System.setProperty(
                SystemProperties.NET_STATS_FILE, "src/test/resources/dev");
    }

    /**
     * Test to ensure network statistics are read correctly.
     */
    @Test
    public void shouldGetNetStats() {
        // Given/when
        NetStats actualStats = SystemMetrics.getNetStats();

        // Then
        NetStats expectedStats = new NetStats(12345678, 87654321);
        assertEquals(expectedStats, actualStats);
    }
}
