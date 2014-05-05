package ee.cyber.sdsb.common.util;

import java.io.File;
import java.io.InputStream;
import java.net.URL;

public class ResourceUtils {

    public static URL getClasspathResource(String resourceName) {
        return ResourceUtils.class.getClassLoader().getResource(resourceName);
    }

    public static InputStream getClasspathResourceStream(String resourceName) {
        return ResourceUtils.class.getClassLoader().getResourceAsStream(
                resourceName);
    }

    public static String getFullPathFromFileName(String fileName) {
        String path = new File(fileName).getAbsolutePath();
        return path.substring(0, path.lastIndexOf(File.separator))
                + File.separator;
    }

    public static String getFileNameFromFullPath(String fullPath) {
        return new File(fullPath).getName();
    }
}
