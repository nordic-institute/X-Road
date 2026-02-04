/*
 * The MIT License
 *
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
package org.niis.xroad.globalconf.util;

import org.niis.xroad.common.exception.ConflictException;
import org.niis.xroad.globalconf.model.ConfigurationLocation;
import org.niis.xroad.globalconf.model.ConfigurationSource;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_DOWNLOAD_URL_FORMAT;

public final class GlobalConfUtils {
    private static final Pattern CONF_PATTERN = Pattern.compile("http://[^/]*/");
    private static final String HTTP = "http";
    private static final String HTTPS = "https";

    private GlobalConfUtils() {
    }

    public static String getConfigurationDirectory(ConfigurationSource source) {
        var firstHttpDownloadUrl = source.getLocations().stream()
                .map(ConfigurationLocation::getDownloadURL)
                .filter(GlobalConfUtils::startWithHttpAndNotWithHttps).findFirst();
        if (firstHttpDownloadUrl.isPresent()) {
            Matcher matcher = CONF_PATTERN.matcher(firstHttpDownloadUrl.get());
            if (matcher.find()) {
                return firstHttpDownloadUrl.get().substring(matcher.end());
            }
        }
        throw new ConflictException(INVALID_DOWNLOAD_URL_FORMAT.build());
    }

    public static boolean startWithHttpAndNotWithHttps(String url) {
        return url.startsWith(HTTP) && !url.startsWith(HTTPS);
    }
}
