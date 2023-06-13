/**
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
package ee.ria.xroad.common;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Stores X-Road software version number
 */
@Slf4j
public final class Version {

    private static final String RELEASE = "RELEASE";
    private static final int VERSION_STRING_SUFFIX_LENGTH = 3;
    private static final String JAVA_DEFAULT_RUNTIME_NAME = "Java";

    public static final String JAVA_VERSION_PROPERTY = "java.version";
    public static final String JAVA_RUNTIME_NAME_PROPERTY = "java.runtime.name";
    public static final String JAVA_RUNTIME_VERSION_PROPERTY = "java.runtime.version";
    public static final String JAVA_VENDOR_PROPERTY = "java.vendor";

    public static final int MIN_SUPPORTED_JAVA_VERSION = 11;
    public static final int MAX_SUPPORTED_JAVA_VERSION = 11;

    public static final String XROAD_VERSION;
    public static final String BUILD_IDENTIFIER;
    public static final String JAVA_VENDOR;
    public static final String JAVA_RUNTIME_VERSION;

    static {
        Properties props = new Properties();

        try (InputStream inputStream = Version.class.getResourceAsStream("/version.properties")) {
            props.load(inputStream);
        } catch (IOException e) {
            log.error("Could not read version.properties", e);
        }

        String version = props.getProperty("version", "");

        String buildType = props.getProperty("buildType", "");
        String commitDate = props.getProperty("gitCommitDate", "");
        String commitHash = props.getProperty("gitCommitHash", "");

        StringBuilder sb = new StringBuilder(buildType);

        if (!commitDate.isEmpty()) {
            sb.append("-").append(commitDate);
        }

        if (!commitHash.isEmpty()) {
            if (commitDate.isEmpty()) {
                sb.append("-");
            }
            sb.append(commitHash);
        }

        BUILD_IDENTIFIER = sb.toString();

        if (buildType.equals(RELEASE)) {
            XROAD_VERSION = version;
        } else {
            XROAD_VERSION = String.format("%s-%s", version, BUILD_IDENTIFIER);
        }

        JAVA_VENDOR = System.getProperty(JAVA_VENDOR_PROPERTY);
        JAVA_RUNTIME_VERSION = System.getProperty(JAVA_RUNTIME_VERSION_PROPERTY, version);
    }

    /**
     * Outputs version information and a warning if JVM version is not in the given range
     */
    public static void outputVersionInfo(String appName) {
        // print app name + version and java vendor name + runtime version
        int javaVersion = readJavaVersion();
        String runtimeName = System.getProperty(JAVA_RUNTIME_NAME_PROPERTY, JAVA_DEFAULT_RUNTIME_NAME);
        String vendorVersion =  JAVA_VENDOR != null ? javaVersion + " "  + JAVA_RUNTIME_VERSION : JAVA_RUNTIME_VERSION;

        log.info(String.format("%s %s (%s %s)", appName, XROAD_VERSION, runtimeName, vendorVersion));
        if (javaVersion < MIN_SUPPORTED_JAVA_VERSION || javaVersion > MAX_SUPPORTED_JAVA_VERSION) {
            if (MIN_SUPPORTED_JAVA_VERSION == MAX_SUPPORTED_JAVA_VERSION) {
                log.warn("Warning! Running on unsupported Java version {}. Java version {} is currently supported.",
                        javaVersion, MIN_SUPPORTED_JAVA_VERSION);
            } else {
                log.warn(
                        "Warning! Unsupported Java version {}. Java versions {} - {} are currently supported.",
                        javaVersion, MIN_SUPPORTED_JAVA_VERSION, MAX_SUPPORTED_JAVA_VERSION);
            }
        }
    }

    public static int readJavaVersion() {
        String version = System.getProperty(JAVA_VERSION_PROPERTY);
        // java.version system property exists in every JVM
        if (version.startsWith("1.")) {
            // Java 8 or lower has format: 1.6.0_23, 1.7.0, 1.7.0_80, 1.8.0_211
            version = version.substring(2, VERSION_STRING_SUFFIX_LENGTH);
        } else {
            // Java 9 or higher has format: 9.0.1, 11.0.4, 12, 12.0.1
            int dot = version.indexOf(".");
            if (dot != -1) {
                version = version.substring(0, dot);
            }
        }
        try {
            return Integer.parseInt(version);
        } catch (NumberFormatException ex) {
            log.error("Error interpreting Java version", ex);
        }
        return 0; // this should actually never happen
    }

    private Version() {
    }
}
