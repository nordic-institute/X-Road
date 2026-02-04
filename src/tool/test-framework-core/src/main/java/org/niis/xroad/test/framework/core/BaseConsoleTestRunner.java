/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package org.niis.xroad.test.framework.core;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.test.framework.core.config.TestFrameworkConfigSource;
import org.niis.xroad.test.framework.core.config.TestFrameworkCoreProperties;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
public abstract class BaseConsoleTestRunner {

    protected abstract String getTestClassName();

    protected void run() {
        log.info("Launching JUnit Console Launcher");
        TestFrameworkConfigSource.enableCLI();
        var coreProps = TestFrameworkConfigSource.getInstance().getCoreProperties();
        extractTestResources(coreProps);
        org.junit.platform.console.ConsoleLauncher.main(getConsoleArgs(coreProps));
    }

    protected String[] getConsoleArgs(TestFrameworkCoreProperties coreProperties) {
        return new String[]{
                "execute",
                "--classpath", ".",
                "--select-class", getTestClassName(),
                "--reports-dir", "%s/test-results".formatted(coreProperties.workingDir())
        };
    }

    /**
     * Extract test resources to a local directory for JAR execution.
     * This method creates a 'xroad-test-resources' directory in the system temp
     * directory
     * and copies the required resources from the classpath.
     * Works both in JAR execution and IDE development.
     */
    private void extractTestResources(TestFrameworkCoreProperties coreProperties) {
        try {
            Path tempDir = Paths.get(coreProperties.resourceDir()).toAbsolutePath();

            Files.createDirectories(tempDir);
            log.info("Created test-resources directory: {}", tempDir);

            // Copy resources
            var resourcesToCopy = getResourcesToExtract();
            for (String resource : resourcesToCopy) {
                copyResourceToFile(resource, tempDir);
            }

            log.info("Successfully extracted {} test resources", resourcesToCopy.length);
        } catch (IOException e) {
            log.error("Failed to extract test resources", e);
            throw new IllegalStateException("Failed to extract test resources", e);
        }
    }

    /**
     * Override this method to specify which classpath resources should be extracted
     * to the test-resources directory for JAR execution.
     *
     * @return array of classpath resource paths to extract
     */
    protected String[] getResourcesToExtract() {
        return new String[]{
                "compose.intTest.yaml",
                ".env",
                "container-files/"
        };
    }

    /**
     * Copy a classpath resource to the test-resources directory.
     * Handles both files and directories recursively.
     */
    private static void copyResourceToFile(String resourcePath, Path targetDir) throws IOException {
        // Handle directories recursively
        if (resourcePath.endsWith("/")) {
            copyDirectoryFromClasspath(resourcePath, targetDir);
        } else {
            copySingleFileFromClasspath(resourcePath, targetDir);
        }
    }

    private static void copySingleFileFromClasspath(String resourcePath, Path targetDir) throws IOException {
        try (InputStream inputStream = BaseConsoleTestRunner.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Resource not found in classpath: " + resourcePath);
            }

            Path targetPath = targetDir.resolve(resourcePath);
            Files.createDirectories(targetPath.getParent());
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            setPermissions(targetPath);
            log.debug("Extracted file: {} -> {}", resourcePath, targetPath);
        }
    }

    private static void copyDirectoryFromClasspath(String resourcePath, Path targetDir) throws IOException {
        // Remove trailing slash
        String cleanPath = resourcePath.endsWith("/") ? resourcePath.substring(0, resourcePath.length() - 1)
                : resourcePath;

        // Get all resources under this path
        var classLoader = BaseConsoleTestRunner.class.getClassLoader();
        var resources = classLoader.getResources(cleanPath);

        while (resources.hasMoreElements()) {
            var url = resources.nextElement();
            if ("jar".equals(url.getProtocol())) {
                // Handle JAR resources
                copyDirectoryFromJar(url, cleanPath, targetDir);
            }
        }
    }

    private static void copyDirectoryFromJar(java.net.URL jarUrl, String resourcePath, Path targetDir)
            throws IOException {
        // For JAR resources, we need to list all files under the path
        // This is a simplified approach - in practice, you might need a more robust
        // solution
        var jarConnection = jarUrl.openConnection();
        if (jarConnection instanceof java.net.JarURLConnection jarConn) {
            var jarFile = jarConn.getJarFile();
            var entries = jarFile.entries();

            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                var entryName = entry.getName();

                if (entryName.startsWith(resourcePath + "/") && !entry.isDirectory()) {
                    // Extract this file
                    String relativePath = entryName.substring(resourcePath.length() + 1);
                    try (var inputStream = jarFile.getInputStream(entry)) {
                        Path targetPath = targetDir.resolve(resourcePath).resolve(relativePath);
                        Files.createDirectories(targetPath.getParent());
                        Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
                        setPermissions(targetPath);
                        log.debug("Extracted JAR resource: {} -> {}", entryName, targetPath);
                    }
                }
            }
        }
    }

    private static void setPermissions(Path path) {
        if (path.toString().endsWith(".sh") && !path.toFile().setExecutable(true, false)) {
            log.warn("Failed to set executable permissions for {}", path);
        }
    }

}
