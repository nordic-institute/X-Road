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
package ee.ria.xroad.common.conf.globalconf;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.apache.http.client.utils.URIBuilder;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;


@RequiredArgsConstructor
@Slf4j
abstract class LocationVersionResolver {
    private static final String VERSION_QUERY_PARAMETER = "version";
    private final ConfigurationLocation location;

    static LocationVersionResolver fixed(ConfigurationLocation location, int version) {
        return new FixedVersionResolver(location, version);
    }
    static LocationVersionResolver range(ConfigurationLocation location, int minVersion, int maxVersion) {
        return new VersionRangeResolver(location, minVersion, maxVersion);
    }

    abstract String chooseVersion(String url) throws Exception;

    ConfigurationLocation toVersionedLocation() throws Exception {
        String versionedUrl = chooseVersion(location.getDownloadURL());
        return new ConfigurationLocation(
                location.getInstanceIdentifier(), versionedUrl, location.getVerificationCerts());
    }


    private static class VersionRangeResolver extends LocationVersionResolver {
        private final int minVersion;
        private final int maxVersion;

        VersionRangeResolver(ConfigurationLocation location, int minVersion, int maxVersion) {
            super(location);
            this.minVersion = minVersion;
            this.maxVersion = maxVersion;
        }

        String chooseVersion(String url) throws IOException, URISyntaxException {
            URIBuilder uriBuilder = new URIBuilder(url);
            if (versionParameterPresent(uriBuilder)) return url;

            int version = maxVersion;

            while (version >= minVersion) {
                log.debug("Determining whether global conf version {} is available for {}", version, uriBuilder);
                uriBuilder.setParameter(VERSION_QUERY_PARAMETER, String.valueOf(version));
                if (version > minVersion) {
                    var locationExists = checkVersionLocationExists(new URL(uriBuilder.toString()));
                    if (locationExists) {
                        log.info("Using Global conf version {}", version);
                        return uriBuilder.toString();
                    }
                } else { // version == minVersion
                    uriBuilder.setParameter(VERSION_QUERY_PARAMETER, String.valueOf(minVersion));
                }
                version--;
            }
            return uriBuilder.toString();
        }

        private boolean checkVersionLocationExists(URL url) throws IOException {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) url.openConnection();
                ConfigurationHttpUrlConnectionConfig.apply(connection);
                return connection.getResponseCode() != HttpStatus.SC_NOT_FOUND;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        private boolean versionParameterPresent(URIBuilder uriBuilder) {
            if (uriBuilder.getQueryParams().stream().anyMatch(param -> VERSION_QUERY_PARAMETER.equals(param.getName()))) {
                log.trace("Respecting the already existing global conf \"version\" query parameter in the URL.");
                return true;
            }
            return false;
        }
    }

    private static class FixedVersionResolver extends LocationVersionResolver {
        private final int version;

        FixedVersionResolver(ConfigurationLocation location, int version) {
            super(location);
            this.version = version;
        }

        @Override
        String chooseVersion(String url) throws URISyntaxException {
            return new URIBuilder(url)
                    .setParameter(VERSION_QUERY_PARAMETER, String.valueOf(version))
                    .toString();
        }
    }

}
