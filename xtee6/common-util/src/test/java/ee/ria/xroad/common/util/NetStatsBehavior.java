/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
