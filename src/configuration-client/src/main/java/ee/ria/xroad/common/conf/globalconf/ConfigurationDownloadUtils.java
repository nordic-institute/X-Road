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
package ee.ria.xroad.common.conf.globalconf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class ConfigurationDownloadUtils {

    private static final String HTTP = "http";
    private static final String HTTPS = "https";

    private ConfigurationDownloadUtils() {
    }

    public static List<ConfigurationLocation> shuffleLocationsPreferHttps(List<ConfigurationLocation> locations) {
        List<ConfigurationLocation> urls = new ArrayList<>(getLocationUrls(locations));
        List<ConfigurationLocation> httpsUrls = urls.stream()
                .filter(location -> startWithHttpAndNotWithHttps(location.getDownloadURL()))
                .map(location -> new ConfigurationLocation(
                        location.getInstanceIdentifier(),
                        location.getDownloadURL().replaceFirst(HTTP, HTTPS),
                        location.getVerificationCerts()))
                .collect(Collectors.toList());
        Collections.shuffle(urls);
        Collections.shuffle(httpsUrls);
        List<ConfigurationLocation> mixedLocations = new ArrayList<>();
        mixedLocations.addAll(httpsUrls);
        mixedLocations.addAll(urls);
        return mixedLocations;
    }

    private static List<ConfigurationLocation> getLocationUrls(List<ConfigurationLocation> locations) {
        return locations.stream()
                .filter(location -> notStartWithHttps(location.getDownloadURL()))
                .toList();
    }

    public static boolean notStartWithHttps(String url) {
        return !url.startsWith(HTTPS);
    }

    public static boolean startWithHttpAndNotWithHttps(String url) {
        return url.startsWith(HTTP) && !url.startsWith(HTTPS);
    }
}
