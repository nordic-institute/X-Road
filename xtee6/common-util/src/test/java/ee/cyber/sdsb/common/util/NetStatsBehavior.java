package ee.cyber.sdsb.common.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NetStatsBehavior {

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
