package ee.ria.xroad.common.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests to verify NET statistics calculator behavior.
 */
public class NetStatsBehavior {

    /**
     * Tests whether diff function behaves as expected.
     */
    @Test
    public void shouldFindDiffNormally() {
        // Given
        NetStats previous = new NetStats(1, 3);
        NetStats current = new NetStats(3, 4);

        // When
        NetStats diff = NetStats.diff(current, previous);

        // Then
        NetStats expectedDiff = new NetStats(2, 1);
        assertEquals(expectedDiff, diff);
    }

    /**
     * Tests whether negative values are eliminated.
     */
    @Test
    public void shouldEliminateNegativesFromDiff() {
        // Given
        NetStats previous = new NetStats(5, 7);
        NetStats current = new NetStats(3, 4);

        // When
        NetStats diff = NetStats.diff(current, previous);

        // Then
        NetStats expectedDiff = new NetStats(0, 0);
        assertEquals(expectedDiff, diff);
    }
}
