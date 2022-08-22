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
package ee.ria.xroad.common.conf.globalconf;

import ee.ria.xroad.common.CodedException;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.operator.DigestCalculator;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static ee.ria.xroad.common.ErrorCodes.X_IO_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_MALFORMED_GLOBALCONF;
import static ee.ria.xroad.common.util.CryptoUtils.createDigestCalculator;
import static ee.ria.xroad.common.util.CryptoUtils.decodeBase64;
import static ee.ria.xroad.common.util.CryptoUtils.encodeBase64;
import static ee.ria.xroad.common.util.CryptoUtils.getAlgorithmId;

/**
 * Downloads configuration directory from a configuration location defined
 * in the configuration anchor.
 *
 * When there is only one configuration location in the configuration anchor, it
 * is used. If there is more than one configuration location, then, for
 * high-availability concerns, list of configuration locations is shuffled and
 * then traversed to find the first location where configuration * can be
 * downloaded. The successful location is remembered and used first next time
 * the configuration is downloaded.
 */
@Slf4j
class ConfigurationDownloader {

    public static final int READ_TIMEOUT = 30000;

    protected final FileNameProvider fileNameProvider;

    private final Map<ConfigurationSource, ConfigurationLocation> lastSuccessfulLocation = new HashMap<>();

    ConfigurationDownloader(String globalConfigurationDir) {
        fileNameProvider = new FileNameProviderImpl(globalConfigurationDir);
    }

    ConfigurationParser getParser() {
        return new ConfigurationParser();
    }

    /**
     * Downloads the configuration from the given configuration source.
     *
     * @param source the configuration source
     * @param contentIdentifiers the content identifier to include
     * @return download result object which contains the state of the download and in case of success
     * the downloaded files.
     */
    DownloadResult download(ConfigurationSource source, String... contentIdentifiers) {
        DownloadResult result = new DownloadResult();

        for (ConfigurationLocation location : getLocations(source)) {
            try {
                Configuration config = download(location, contentIdentifiers);
                rememberLastSuccessfulLocation(location);
                return result.success(config);
            } catch (Exception e) {
                result.addFailure(location, e);
            }
        }

        return result.failure();
    }

    private void rememberLastSuccessfulLocation(ConfigurationLocation location) {
        log.trace("rememberLastSuccessfulLocation source={} location={}", location.getSource(), location);
        lastSuccessfulLocation.put(location.getSource(), location);
    }

    private List<ConfigurationLocation> getLocations(ConfigurationSource source) {
        List<ConfigurationLocation> result = new ArrayList<>();

        preferLastSuccessLocation(source, result);

        List<ConfigurationLocation> randomized = new ArrayList<>(source.getLocations());
        Collections.shuffle(randomized);
        result.addAll(randomized);

        result.removeIf(Objects::isNull);

        return result;
    }

    private void preferLastSuccessLocation(ConfigurationSource source, List<ConfigurationLocation> result) {
        if (!lastSuccessfulLocation.isEmpty()) {
            log.trace("preferLastSuccessLocation source={} location={}", source, lastSuccessfulLocation.get(source));
            result.add(lastSuccessfulLocation.get(source));
        } else {
            log.trace("preferLastSuccessLocation lastSuccessfulLocation is empty");
        }
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

        for (ConfigurationFile file : configuration.getFiles()) {
            Path contentFileName = fileNameProvider.getFileName(file);
            if (shouldDownload(file, contentFileName)) {
                byte[] content = downloadContent(location, file);

                verifyContent(content, file);
                handleContent(content, file);

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
            log.error("Error deleting file in directory " + instanceDirectory, e);
        }

    }

    protected static class DownloadedContent {
        ConfigurationFile file;

        // if null content was not downloaded as it was not changed
        byte[] content;

        public DownloadedContent(ConfigurationFile file, byte[] content) {
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
     * @param file current configuration file
     * @return boolean value of whether the files should be downloaded or not
     * @throws Exception in case of unexpected exception happens
     */
    boolean shouldDownload(ConfigurationFile configurationFile, Path file) throws Exception {
        log.trace("shouldDownload({}, {})", configurationFile.getContentLocation(), configurationFile.getHash());

        if (Files.exists(file)) {
            String contentHash = configurationFile.getHash();
            String existingHash = encodeBase64(hash(file, configurationFile.getHashAlgorithmId()));
            if (!StringUtils.equals(existingHash, contentHash)) {
                log.trace("Downloading {} because file has changed ({} != {})",
                        configurationFile.getContentLocation(), existingHash, contentHash);
                return true;
            } else {
                return false;
            }
        }

        log.trace("Downloading {} because file {} does not exist locally",
                configurationFile.getContentLocation(), file);
        return true;
    }

    byte[] downloadContent(ConfigurationLocation location, ConfigurationFile file) throws Exception {
        URLConnection connection = getDownloadURLConnection(getDownloadURL(location, file));
        log.info("Downloading content from {}", connection.getURL());
        try (InputStream in = connection.getInputStream()) {
            return IOUtils.toByteArray(in);
        }
    }

    void verifyContent(byte[] content, ConfigurationFile file) throws Exception {
        String algoId = getAlgorithmId(file.getHashAlgorithmId());
        log.trace("verifyContent({}, {})", file.getHash(), algoId);

        DigestCalculator dc = createDigestCalculator(algoId);
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

    void handleContent(byte[] content, ConfigurationFile file) throws Exception {
        switch (file.getContentIdentifier()) {
            case ConfigurationConstants.CONTENT_ID_PRIVATE_PARAMETERS:
                PrivateParametersV2 privateParameters = new PrivateParametersV2(content);
                handlePrivateParameters(privateParameters, file);
                break;
            case ConfigurationConstants.CONTENT_ID_SHARED_PARAMETERS:
                SharedParametersV2 sharedParameters = new SharedParametersV2(content);
                handleSharedParameters(sharedParameters, file);
                break;
            default:
                break;
        }
    }

    void handlePrivateParameters(PrivateParametersV2 privateParameters, ConfigurationFile file) {
        verifyInstanceIdentifier(privateParameters.getInstanceIdentifier(), file);
    }

    void handleSharedParameters(SharedParametersV2 sharedParameters, ConfigurationFile file) {
        verifyInstanceIdentifier(sharedParameters.getInstanceIdentifier(), file);
    }

    void persistContent(byte[] content, Path destination, ConfigurationFile file) throws Exception {
        log.info("Saving {} to {}", file, destination);

        ConfigurationDirectory.save(destination, content, file.getMetadata());
    }

    void updateExpirationDate(Path destination, ConfigurationFile file) throws Exception {
        log.trace("{} expires {}", file, file.getExpirationDate());

        ConfigurationDirectory.saveMetadata(destination, file.getMetadata());
    }

    void verifyInstanceIdentifier(String instanceIdentifier, ConfigurationFile file) {
        if (StringUtils.isBlank(file.getInstanceIdentifier())) {
            return;
        }

        if (!instanceIdentifier.equals(file.getInstanceIdentifier())) {
            throw new CodedException(X_MALFORMED_GLOBALCONF,
                    "Content part %s has invalid instance identifier "
                            + "(expected %s, but was %s)", file,
                            file.getInstanceIdentifier(), instanceIdentifier);
        }
    }

    public static URL getDownloadURL(ConfigurationLocation location, ConfigurationFile file) throws Exception {
        return new URI(location.getDownloadURL()).resolve(file.getContentLocation()).toURL();
    }

    public static URLConnection getDownloadURLConnection(URL url) throws IOException {
        URLConnection connection = url.openConnection();
        connection.setReadTimeout(READ_TIMEOUT);
        return connection;
    }

    // ------------------------------------------------------------------------

    static byte[] hash(Path file, String algoId) throws Exception {
        DigestCalculator dc = createDigestCalculator(getAlgorithmId(algoId));

        try (InputStream in = Files.newInputStream(file)) {
            IOUtils.copy(in, dc.getOutputStream());
            return dc.getDigest();
        }
    }
}
