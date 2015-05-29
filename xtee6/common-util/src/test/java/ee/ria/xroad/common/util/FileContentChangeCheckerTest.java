package ee.ria.xroad.common.util;

import java.io.File;

import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Unit test for FileContentChangeChecker.
 */
public class FileContentChangeCheckerTest {

    /**
     * Tests whether the file content changes are detected
     * @throws Exception if error occurs
     */
    @Test
    public void checkChanges() throws Exception {
        FileContentChangeChecker checker =
                new FileContentChangeChecker("mock") {
            @Override
            protected String calculateConfFileChecksum(File file)
                    throws Exception {
                return "foo";
            }
        };

        FileContentChangeChecker spy = spy(checker);

        assertFalse("Should not have changed yet", spy.hasChanged());

        when(spy.calculateConfFileChecksum(Mockito.any())).thenReturn("bar");

        assertTrue("Should have changed", spy.hasChanged());

        when(spy.calculateConfFileChecksum(Mockito.any())).thenReturn("foo");

        assertTrue("Should have changed", spy.hasChanged());
    }
}
