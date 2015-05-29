package ee.cyber.xroad.mediator;

import java.io.InputStream;

import ee.ria.xroad.common.util.ResourceUtils;

/**
 * Contains utility methods for loading test resources.
 */
public final class TestResources {

    private TestResources() {
    }

    /**
     * @param fileName the file name
     * @return the input stream of the resource with the given file name
     * @throws Exception in case of any errors
     */
    public static InputStream get(String fileName) throws Exception {
        if (fileName != null) {
            InputStream is = ResourceUtils.getClasspathResourceStream(fileName);
            if (is == null) {
                throw new RuntimeException("Failed to get classpath resource '"
                        + fileName + "' as stream");
            }

            return is;
        }

        return null;
    }
}
