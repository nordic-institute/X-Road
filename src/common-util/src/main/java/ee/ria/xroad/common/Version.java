/**
 * The MIT License
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

import java.util.ResourceBundle;

/**
 * Stores X-Road software version number
 */
public final class Version {

    private static ResourceBundle versionProperties = ResourceBundle.getBundle("version");

    public static final String XROAD_VERSION = versionProperties.getString("version");

    private Version() {
    }

    /**
     * @return X-Road version, Git commit date and hash
     */
    public static String getFullVersion() {
        StringBuilder sb = new StringBuilder(XROAD_VERSION);

        String commitDate = versionProperties.getString("gitCommitDate");
        String commitHash = versionProperties.getString("gitCommitHash");

        if (commitDate != null) {
            sb.append(".").append(commitDate);
        }

        if (commitHash != null) {
            sb.append(".").append(commitHash);
        }

        return sb.toString();
    }
}
