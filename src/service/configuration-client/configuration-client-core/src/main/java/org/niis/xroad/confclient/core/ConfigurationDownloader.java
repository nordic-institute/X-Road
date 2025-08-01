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
package org.niis.xroad.confclient.core;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.operator.DigestCalculator;
import org.niis.xroad.globalconf.model.ConfigurationConstants;
import org.niis.xroad.globalconf.model.ConfigurationDirectory;
import org.niis.xroad.globalconf.model.ConfigurationLocation;
import org.niis.xroad.globalconf.model.ConfigurationSource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static ee.ria.xroad.common.ErrorCodes.X_IO_ERROR;
import static ee.ria.xroad.common.SystemProperties.CURRENT_GLOBAL_CONFIGURATION_VERSION;
import static ee.ria.xroad.common.SystemProperties.MINIMUM_SUPPORTED_GLOBAL_CONFIGURATION_VERSION;
import static ee.ria.xroad.common.crypto.Digests.createDigestCalculator;
import static ee.ria.xroad.common.util.EncoderUtils.decodeBase64;
import static ee.ria.xroad.common.util.EncoderUtils.encodeBase64;

/**
 * Downloads configuration directory from a configuration location defined
 * in the configuration anchor.
 * <p>
 * When there is only one configuration location in the configuration anchor, it
 * is used. If there is more than one configuration location, then, for
 * high-availability concerns, list of configuration locations is shuffled and
 * then traversed to find the first location where configuration * can be
 * downloaded. The successful location is remembered and used first next time
 * the configuration is downloaded.
 */
@Slf4j
public class ConfigurationDownloader {

    protected final FileNameProvider fileNameProvider;
    private final HttpUrlConnectionConfigurer connectionConfigurer = new HttpUrlConnectionConfigurer();
    private final Map<String, ConfigurationLocation> successfulLocations = new HashMap<>();
    private final SharedParametersConfigurationLocations sharedParametersConfigurationLocations;

    @Getter
    private final Integer configurationVersion;

    ConfigurationDownloader(String globalConfigurationDir, int configurationVersion) {
        fileNameProvider = new FileNameProviderImpl(globalConfigurationDir);
        this.sharedParametersConfigurationLocations = new SharedParametersConfigurationLocations(fileNameProvider);
        this.configurationVersion = configurationVersion;
    }

    ConfigurationDownloader(String globalConfigurationDir) {
        fileNameProvider = new FileNameProviderImpl(globalConfigurationDir);
        this.sharedParametersConfigurationLocations = new SharedParametersConfigurationLocations(fileNameProvider);
        this.configurationVersion = null;
    }

    public ConfigurationDownloader(FileNameProvider fileNameProvider) {
        this.fileNameProvider = fileNameProvider;
        this.sharedParametersConfigurationLocations = new SharedParametersConfigurationLocations(fileNameProvider);
        this.configurationVersion = null;
    }

    ConfigurationParser getParser() {
        return new ConfigurationParser(this);
    }

    /**
     * Downloads the configuration from the given configuration source.
     *
     * @param source             the configuration source
     * @param contentIdentifiers the content identifier to include
     * @return download result object which contains the state of the download and in case of success
     * the downloaded files.
     */
    DownloadResult download(ConfigurationSource source, String... contentIdentifiers) {
        log.debug("download with contentIdentifiers: {}", (Object) contentIdentifiers);

        List<ConfigurationLocation> sharedParameterLocations = sharedParametersConfigurationLocations.get(source);

        List<ConfigurationLocation> locations = new ArrayList<>();
        if (!sharedParameterLocations.isEmpty()) {
            locations.addAll(ConfigurationDownloadUtils.shuffleLocationsPreferHttps(sharedParameterLocations));
            log.debug("sharedParameterLocations.size = {}", sharedParameterLocations.size());
        }

        locations.addAll(ConfigurationDownloadUtils.shuffleLocationsPreferHttps(source.getLocations()));

        Optional<String> prevCachedKey = findLocationWithPreviousSuccess(locations)
                .map(locationWithPreviousSuccess -> {
                    locations.addFirst(successfulLocations.get(locationWithPreviousSuccess.getDownloadURL()));
                    log.debug("Previously cached key: {}", locationWithPreviousSuccess.getDownloadURL());
                    return locationWithPreviousSuccess.getDownloadURL();
                });

        return downloadResult(prevCachedKey.orElse(null), locations, contentIdentifiers);
    }

    private DownloadResult downloadResult(String prevCachedKey, List<ConfigurationLocation> locations, String... contentIdentifiers) {
        DownloadResult result = new DownloadResult();
        for (ConfigurationLocation location : locations) {
            String cacheKey = prevCachedKey != null ? prevCachedKey : location.getDownloadURL();

            try {
                location = toVersionedLocation(location);
                Configuration config = download(location, contentIdentifiers);
                rememberLastSuccessfulLocation(cacheKey, location);
                return result.success(config);
            } catch (Exception e) {
                log.warn("Unable to download Global Configuration. Because {}", e.toString());
                successfulLocations.remove(cacheKey);
                result.addFailure(location, e);
            }
        }
        return result.failure();
    }

    private Optional<ConfigurationLocation> findLocationWithPreviousSuccess(List<ConfigurationLocation> locations) {
        for (ConfigurationLocation location : locations) {
            ConfigurationLocation successfulLocation = successfulLocations.get(location.getDownloadURL());
            if (successfulLocation != null) {
                log.trace("Found location={} which corresponds to previously successful location={}", location, successfulLocation);
                return Optional.of(location);
            }
        }
        return Optional.empty();
    }

    private void rememberLastSuccessfulLocation(String cacheKey, ConfigurationLocation location) {
        log.trace("rememberLastSuccessfulLocation cache key = {} location = {}", cacheKey, location);
        successfulLocations.put(cacheKey, location);
    }

    Configuration download(ConfigurationLocation location, String[] contentIdentifiers) throws Exception {
        log.info("Downloading configuration from {}", location.getDownloadURL());

        Configuration configuration = getParser().parse(location, contentIdentifiers);

        // first download all parts into memory and verify then
        List<DownloadedContent> downloadedContents = downloadAllContent(configuration);

        // when everything is ok save contents and/or update expiry dates
        Set<Path> neededFiles = persistAllContent(downloadedContents);

        deleteExtraFiles(configuration.getInstanceIdentifier(), neededFiles);

        return configuration;
    }

    /**
     * Download all configuration files if the conditions are met {@link #shouldDownload(ConfigurationFile, Path)}.
     *
     * @param configuration configuration object with details about the configuration download location
     * @return list of downloaded content
     * @throws Exception in case downloading or handling a file fails
     */
    List<DownloadedContent> downloadAllContent(Configuration configuration) throws Exception {
        log.trace("downloadAllContent");

        List<DownloadedContent> result = new ArrayList<>();
        ConfigurationLocation location = configuration.getLocation();

        var contentHandler = ContentHandler.forVersion(configuration.getVersion());

        for (ConfigurationFile file : configuration.getFiles()) {
            Path contentFileName = fileNameProvider.getFileName(file);
            if (shouldDownload(file, contentFileName)) {
                byte[] content = downloadContent(location, file);

                verifyContent(content, file);
                validateContent(file);
                contentHandler.handleContent(content, file);

                result.add(new DownloadedContent(file, content));
            } else {
                log.trace("{} is up to date", file.getContentLocation());
                validateContent(file);
                result.add(new DownloadedContent(file, null));
            }
        }

        return result;
    }

    Set<Path> persistAllContent(List<DownloadedContent> downloadedContents) throws Exception {
        Set<Path> result = new HashSet<>();
        for (DownloadedContent downloadedContent : downloadedContents) {
            Path contentFileName = fileNameProvider.getFileName(downloadedContent.file);
            if (downloadedContent.content != null) {
                persistContent(downloadedContent.content, contentFileName, downloadedContent.file);
            } else {
                updateExpirationDate(contentFileName, downloadedContent.file);
            }
            result.add(contentFileName);
            result.add(contentFileName.resolveSibling(contentFileName.getFileName()
                    + ConfigurationConstants.FILE_NAME_SUFFIX_METADATA));
        }
        return result;
    }

    void deleteExtraFiles(String instanceIdentifier, Set<Path> neededFiles) {
        Path instanceDirectory = fileNameProvider.getConfigurationDirectory(instanceIdentifier);
        try {
            try (Stream<Path> fileStream = Files.walk(instanceDirectory)) {
                fileStream
                        .filter(i -> !neededFiles.contains(i))
                        .map(Path::toFile)
                        .filter(File::isFile)
                        .forEach(File::delete);
            }
        } catch (IOException e) {
            log.error("Error deleting file in directory {}", instanceDirectory, e);
        }

    }

    protected static class DownloadedContent {
        ConfigurationFile file;

        // if null content was not downloaded as it was not changed
        byte[] content;

        DownloadedContent(ConfigurationFile file, byte[] content) {
            this.file = file;
            this.content = content;
        }
    }

    /**
     * Checks if the configuration file should be downloaded. The rules to download:
     * i) Configuration file does not exist in the system
     * ii) Configuration file hash is different from the one that system has
     *
     * @param configurationFile new configuration file
     * @param file              current configuration file
     * @return boolean value of whether the files should be downloaded or not
     * @throws Exception in case of unexpected exception happens
     */
    boolean shouldDownload(ConfigurationFile configurationFile, Path file) throws Exception {
        log.trace("shouldDownload({}, {})", configurationFile.getContentLocation(), configurationFile.getHash());

        if (Files.exists(file)) {
            String contentHash = configurationFile.getHash();
            String existingHash = encodeBase64(hash(file, configurationFile.getHashAlgorithmId()));
            if (StringUtils.equals(existingHash, contentHash)) {
                return false;
            } else {
                log.trace("Downloading {} because file has changed ({} != {})",
                        configurationFile.getContentLocation(), existingHash, contentHash);
                return true;
            }
        }

        log.trace("Downloading {} because file {} does not exist locally",
                configurationFile.getContentLocation(), file);
        return true;
    }

    private LocationVersionResolver locationVersionResolver(ConfigurationLocation location) {
        if (configurationVersion == null) {
            return LocationVersionResolver.range(connectionConfigurer, location,
                    MINIMUM_SUPPORTED_GLOBAL_CONFIGURATION_VERSION,
                    CURRENT_GLOBAL_CONFIGURATION_VERSION);
        } else {
            return LocationVersionResolver.fixed(connectionConfigurer, location, configurationVersion);
        }
    }

    private ConfigurationLocation toVersionedLocation(ConfigurationLocation location) throws Exception {
        return this.locationVersionResolver(location).toVersionedLocation();
    }

    byte[] downloadContent(ConfigurationLocation location, ConfigurationFile file) throws Exception {
        URLConnection connection = getDownloadURLConnection(getDownloadURL(location, file));
        log.info("Downloading content from {}", connection.getURL());
        try (InputStream in = connection.getInputStream()) {
            return IOUtils.toByteArray(in);
        }
    }

    void verifyContent(byte[] content, ConfigurationFile file) throws Exception {
        log.trace("verifyContent({}, {})", file.getHash(), file.getHashAlgorithmId());

        DigestCalculator dc = createDigestCalculator(file.getHashAlgorithmId());
        dc.getOutputStream().write(content);

        byte[] hash = dc.getDigest();
        if (!Arrays.equals(hash, decodeBase64(file.getHash()))) {
            log.trace("Content {} hash {} does not match expected hash {}", file, encodeBase64(hash), file.getHash());
            throw new CodedException(X_IO_ERROR, "Failed to verify content integrity (%s)", file);
        }
    }

    void validateContent(ConfigurationFile file) {
        //make possible with current structure to be overridden and validations called
    }

    void persistContent(byte[] content, Path destination, ConfigurationFile file) throws Exception {
        log.info("Saving {} to {}", file, destination);

        ConfigurationDirectory.save(destination, content, file.getMetadata());
    }

    void updateExpirationDate(Path destination, ConfigurationFile file) throws Exception {
        log.trace("{} expires {}", file, file.getExpirationDate());

        ConfigurationDirectory.saveMetadata(destination, file.getMetadata());
    }

    public static URL getDownloadURL(ConfigurationLocation location, ConfigurationFile file) throws Exception {
        return new URI(location.getDownloadURL()).resolve(file.getContentLocation()).toURL();
    }

    public URLConnection getDownloadURLConnection(URL url) throws IOException {
        URLConnection connection = url.openConnection();
        connectionConfigurer.apply((HttpURLConnection) connection);
        return connection;
    }

    // ------------------------------------------------------------------------

    static byte[] hash(Path file, DigestAlgorithm algoUri) throws Exception {
        DigestCalculator dc = createDigestCalculator(algoUri);

        try (InputStream in = Files.newInputStream(file)) {
            IOUtils.copy(in, dc.getOutputStream());
            return dc.getDigest();
        }
    }
}
