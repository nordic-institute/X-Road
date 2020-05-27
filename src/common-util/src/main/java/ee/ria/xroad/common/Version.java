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
    public static final String XROAD_VERSION;
    public static final String BUILD_IDENTIFIER;

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
    }

    private Version() {
    }
}
