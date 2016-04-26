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
