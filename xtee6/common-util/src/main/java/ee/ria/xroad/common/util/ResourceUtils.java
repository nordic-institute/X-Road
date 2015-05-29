package ee.ria.xroad.common.util;

import java.io.File;
import java.io.InputStream;
import java.net.URL;

/**
 * Contains utility methods for loading resources.
 */
public final class ResourceUtils {

    private ResourceUtils() {
    }

    /**
     * Get the URL for a resource located on the classpath.
     * @param resourceName name of the resource
     * @return resource location URL
     */
    public static URL getClasspathResource(String resourceName) {
        return ResourceUtils.class.getClassLoader().getResource(resourceName);
    }

    /**
     * Gets the resource from the classpath as an input stream.
     * @param resourceName name of the resource
     * @return input stream with the resource contents
     */
    public static InputStream getClasspathResourceStream(String resourceName) {
        return ResourceUtils.class.getClassLoader().getResourceAsStream(
                resourceName);
    }

    /**
     * Gets the full path for the given file, excluding the file name.
     * @param fileName name of the file
     * @return String
     */
    public static String getFullPathFromFileName(String fileName) {
        String path = new File(fileName).getAbsolutePath();
        return path.substring(0, path.lastIndexOf(File.separator))
                + File.separator;
    }

    /**
     * Extracts the filename from the full file path.
     * @param fullPath full path of the file
     * @return String
     */
    public static String getFileNameFromFullPath(String fullPath) {
        return new File(fullPath).getName();
    }
}
