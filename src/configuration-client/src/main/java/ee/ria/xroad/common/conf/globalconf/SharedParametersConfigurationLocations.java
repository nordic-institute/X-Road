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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.exception.DataIntegrityException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateEncodingException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ee.ria.xroad.common.conf.globalconf.VersionedConfigurationDirectory.getVersion;
import static org.niis.xroad.common.exception.util.CommonDeviationMessage.INVALID_DOWNLOAD_URL_FORMAT;

@Slf4j
@RequiredArgsConstructor
class SharedParametersConfigurationLocations {
    private static final String HTTP = "http";
    private static final String HTTPS = "https";
    private static final String INTERNAL_CONF = "internalconf";
    private static final String EXTERNAL_CONF = "externalconf";
    private static final Pattern CONF_PATTERN = Pattern.compile("http://[^/]*/");

    private final FileNameProvider fileNameProvider;

    List<ConfigurationLocation> get(ConfigurationSource source) {
        List<ConfigurationLocation> locations = new ArrayList<>();

        try {
            String configurationDirectory = getConfigurationDirectory(source);
            List<SharedParameters.ConfigurationSource> sharedParametersConfigurationSources =
                    getSharedParametersConfigurationSources(source.getInstanceIdentifier());
            locations = sharedParametersConfigurationSources.stream()
                    .map(confSource -> new ConfigurationLocation(
                            source.getInstanceIdentifier(), getDownloadUrl(confSource.getAddress(), configurationDirectory),
                            getVerificationCerts(configurationDirectory, confSource)))
                    .collect(Collectors.toList());
        } catch (CertificateEncodingException | DataIntegrityException | IOException e) {
            log.error("Unable to acquire shared parameters for instance {}", source.getInstanceIdentifier(), e);
        }

        locations.addAll(locations.stream()
                .map(location -> new ConfigurationLocation(
                        location.getInstanceIdentifier(),
                        location.getDownloadURL().replaceFirst(HTTPS, HTTP),
                        location.getVerificationCerts()))
                .toList());
        return locations;
    }

    private String getDownloadUrl(String domainAddress, String configurationDirectory) {
        return String.format("%s://%s/%s", HTTPS, domainAddress, configurationDirectory);
    }

    private String getConfigurationDirectory(ConfigurationSource source) {
        var firstHttpDownloadUrl = source.getLocations().stream()
                .map(ConfigurationLocation::getDownloadURL)
                .filter(ConfigurationDownloadUtils::startWithHttpAndNotWithHttps).findFirst();
        if (firstHttpDownloadUrl.isPresent()) {
            Matcher matcher = CONF_PATTERN.matcher(firstHttpDownloadUrl.get());
            if (matcher.find()) {
                return firstHttpDownloadUrl.get().substring(matcher.end());
            }
        }
        throw new DataIntegrityException(INVALID_DOWNLOAD_URL_FORMAT);
    }

    private List<byte[]> getVerificationCerts(String confLocation, SharedParameters.ConfigurationSource confSource) {
        if (isInternalConfiguration(confLocation)) {
            return confSource.getInternalVerificationCerts();
        } else if (isExternalConfiguration(confLocation)) {
            return confSource.getExternalVerificationCerts();
        }
        return Stream.concat(confSource.getInternalVerificationCerts().stream(), confSource.getExternalVerificationCerts().stream())
                .toList();
    }

    private boolean isInternalConfiguration(String confLocation) {
        return INTERNAL_CONF.equals(confLocation);
    }

    private boolean isExternalConfiguration(String confLocation) {
        return EXTERNAL_CONF.equals(confLocation);
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private List<SharedParameters.ConfigurationSource> getSharedParametersConfigurationSources(String instanceIdentifier)
            throws CertificateEncodingException, IOException {
        Path sharedParamsPath = fileNameProvider.getConfigurationDirectory(instanceIdentifier)
                .resolve(ConfigurationConstants.FILE_NAME_SHARED_PARAMETERS);
        if (Files.exists(sharedParamsPath)) {
            return ParametersProviderFactory.forGlobalConfVersion(getVersion(sharedParamsPath))
                    .sharedParametersProvider(sharedParamsPath, OffsetDateTime.MAX)
                    .getSharedParameters()
                    .getSources();
        }
        return List.of();
    }
}
