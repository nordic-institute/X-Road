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
package ee.ria.xroad.signer.certmanager;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for global conf change checking
 */
public class GlobalConfChangeCheckerTest {

    private static final String CHANGE_KEY = "ocspFreshnessSeconds";

    /**
     * Test global conf change detection
     */
    @Test
    public void testOcspFreshnessChange() {
        GlobalConfChangeChecker checker = new GlobalConfChangeChecker();
        // initially it should report no change
        assertFalse(checker.hasChanged(CHANGE_KEY));
        // change the value, initially it should report no change
        final int change1 = 3600;
        boolean result = checker.addChange(CHANGE_KEY, change1);
        assertFalse(result);
        assertFalse(checker.hasChanged(CHANGE_KEY));
        // insert the same value, it should report no change
        final int change2 = 3600;
        result = checker.addChange(CHANGE_KEY, change2);
        assertFalse(result);
        assertFalse(checker.hasChanged(CHANGE_KEY));
        // add another changed value, it should report change
        final int change3 = 10800;
        result = checker.addChange(CHANGE_KEY, change3);
        assertTrue(result);
        assertTrue(checker.hasChanged(CHANGE_KEY));
    }
}
