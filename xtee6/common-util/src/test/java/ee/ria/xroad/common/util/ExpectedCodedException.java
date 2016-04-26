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
// This class is exact copy of the same class in common-test package.
// It is here because we cannot create circular dependency.

package ee.ria.xroad.common.util;

import org.apache.commons.lang3.StringUtils;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import ee.ria.xroad.common.CodedException;

import static org.junit.Assert.*;

/**
 * JUnit rule that verifies that given block of code throws
 * CodedException with given error code.
 *
 * Usage:
 * <code>
 *     public class FooTest {
 *         // By default, we expect nothing
 *         @Rule
 *         public ExpectedCodedException thrown =
 *                  ExpectedCodedException.none();
*
 *         @Test
 *         public void testThrowsNothing() {
 *         }
 *
 *         @Test
 *         public void testThrowsCodedException() {
 *             thrown.expectError(ErrorCodes.X_FOO_BAR);
 *             doFancyStuff(); // throws CodedException.
 *         }
 *     }
 * </code>
 */
public final class ExpectedCodedException implements TestRule {
    /** What do we expect? */
    private String expected;

    /** Do we expect whole string or just suffix? */
    private boolean expectedSuffix;

    /**
     * @return a Rule that expects no exception to be thrown
     * (identical to behavior without this Rule)
     */
    public static ExpectedCodedException none() {
        return new ExpectedCodedException();
    }

    // Use only none() to construct instances.
    private ExpectedCodedException() {
    }

    /**
     * Expects code to throw CodedException with the exact error code.
     * If given error code is null, expects nothing.
     * @param parts the error code parts
     */
    public void expectError(String ...parts) {
        expected = join(parts);
        expectedSuffix = false;
    }

    /**
     * Expects code to throw CodedException whose error code ends with given
     * suffix. If given error code is null, expects nothing.
     * @param parts the error code parts
     */
    public void expectErrorSuffix(String ...parts) {
        expected = join(parts);
        expectedSuffix = true;
    }

    private static String join(String[] parts) {
        if (parts == null || parts.length == 0 || parts[0] == null) {
            return null;
        }
        return StringUtils.join(parts, ".");
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new ExpectedStatement(base);
    }

    private class ExpectedStatement extends Statement {
        private Statement statement;

        ExpectedStatement(Statement statement) {
            this.statement = statement;
        }

        @Override
        public void evaluate() throws Throwable {
            try {
                statement.evaluate();
            } catch (CodedException e) {
                if (expected == null) {
                    throw e;
                }
                if (expectedSuffix) {
                    assertTrue("CodedException with error code suffix "
                            + expected, e.getFaultCode().endsWith(expected));
                } else {
                    assertEquals("CodedException with error code",
                            expected, e.getFaultCode());
                }
                return;
            }

            if (expected != null) {
                fail("Expected test to throw CodedException with "
                        + (expectedSuffix
                                ? "error code suffix " : "error code ")
                        + expected);
            }
        }
    }
}
