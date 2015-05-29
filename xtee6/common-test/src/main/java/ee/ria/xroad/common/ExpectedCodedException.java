package ee.ria.xroad.common;

import org.apache.commons.lang3.StringUtils;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

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
     * @param parts of the coded exception error code
     */
    public void expectError(String ...parts) {
        expected = join(parts);
        expectedSuffix = false;
    }

    /**
     * Expects code to throw CodedException whose error code ends with given
     * suffix. If given error code is null, expects nothing.
     * @param parts of the coded exception error code
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

        public ExpectedStatement(Statement statement) {
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
            } catch (Throwable th) {
                if (expected == null) {
                    throw th;
                }

                fail("Expected test to throw CodedException, "
                        + "but test threw: " + th);
            }

            if (expected != null) {
                fail("Expected test to throw CodedException with "
                        + (expectedSuffix ? "error code suffix " : "error code ")
                        + expected);
            }
        }
    }
}
