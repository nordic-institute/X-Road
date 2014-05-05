package ee.cyber.sdsb.common.util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FileContentChangeCheckerTest {

    private static final String INITIAL_DATA = "initial data";

    @Test
    public void shouldDetectContentChange() throws Exception {
        MockedChecker checker = new MockedChecker();

        assertFalse("Should have not changed yet", checker.hasChanged());

        checker.setData("foobar");

        assertTrue("Should have changed", checker.hasChanged());
    }

    @Test
    public void shouldDetectContentChangeForLastModified() throws Exception {
        MockedChecker checker = new MockedChecker();

        assertFalse("Should have not changed yet", checker.hasChanged());

        checker.getFile().setLastModified(System.currentTimeMillis() + 1000);

        assertTrue("Should have changed", checker.hasChanged());
    }

    @Test
    public void shouldNotDetectContentChange() throws Exception {
        MockedChecker checker = new MockedChecker();

        assertFalse("Should have not changed yet", checker.hasChanged());

        checker.setData("foobar");
        checker.setData(INITIAL_DATA);

        assertFalse("Should have not changed", checker.hasChanged());
    }

    private class MockedChecker extends FileContentChangeChecker {

        private String data;
        private File file;

        public MockedChecker() throws Exception {
            super("");
        }

        public void setData(String data) {
            this.data = data;
        }

        @Override
        protected File getFile() {
            if (file == null) {
                try {
                    file = File.createTempFile(
                            FileContentChangeCheckerTest.class.getName(),
                            ".tmp");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            return file;
        }

        @Override
        protected InputStream getInputStream(File file) throws Exception {
            return new ByteArrayInputStream(
                    data == null ? INITIAL_DATA.getBytes() : data.getBytes());
        }
    }
}
