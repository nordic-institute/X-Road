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

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.util.TimeUtils;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.conf.globalconf.ConfigurationUtils.escapeInstanceIdentifier;

/**
 * Class for reading global configuration directory. The directory must have subdirectory per instance identifier.
 * Each subdirectory must contain private and/or shared parameters.
 * <br/> When querying the parameters from this class, the parameters XML is checked for modifications and if the XML has
 * been modified, the parameters are reloaded from the XML.
 *
 * @param <T> PrivateParametersProvider
 * @param <S> SharedParametersProvider
 */
@Slf4j
public abstract class VersionableConfigurationDirectory<T extends PrivateParametersProvider, S extends SharedParametersProvider>
        implements ConfigurationDirectory {

    @Getter
    private final Path path;

    @Getter
    private final String instanceIdentifier;

    protected final Map<String, T> privateParameters = new HashMap<>();
    protected final Map<String, S> sharedParameters = new HashMap<>();

    // ------------------------------------------------------------------------

    /**
     * Constructs new directory from the given path.
     * @param directoryPath the path to the directory.
     * @throws Exception if loading configuration fails
     */
    VersionableConfigurationDirectory(String directoryPath) throws Exception {
        this.path = Paths.get(directoryPath);

        instanceIdentifier = loadInstanceIdentifier();

        // empty maps as placeholders
        loadParameters(new HashMap<>(), new HashMap<>());
    }

    /**
     * Constructs new directory from the given path using parts from provided base that have not changed.
     * @param directoryPath the path to the directory.
     * @param base existing configuration directory to look for reusable parameter objects
     * @throws Exception if loading configuration fails
     */
    VersionableConfigurationDirectory(String directoryPath, VersionableConfigurationDirectory<T, S> base) throws Exception {
        this.path = Paths.get(directoryPath);

        instanceIdentifier = loadInstanceIdentifier();

        loadParameters(base.privateParameters, base.sharedParameters);
    }

    /**
     * Reloads the configuration directory. Only files that are new or have changed, are actually loaded.
     * @throws Exception if an error occurs during reload
     */
    private void loadParameters(Map<String, T> basePrivateParams,
                                Map<String, S> baseSharedParams) throws Exception {
        log.trace("Reloading configuration from {}", path);

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path, Files::isDirectory)) {
            for (Path instanceDir : stream) {
                log.trace("Loading parameters from {}", instanceDir);
                loadPrivateParameters(instanceDir, basePrivateParams);
                loadSharedParameters(instanceDir, baseSharedParams);
            }
        }
    }

    /**
     * Returns private parameters for a given instance identifier.
     * @param instanceId the instance identifier
     * @return private parameters or null, if no private parameters exist for given instance identifier
     * @throws Exception if an error occurs while reading parameters
     */
    public PrivateParameters getPrivate(String instanceId) throws Exception {
        String safeInstanceId = escapeInstanceIdentifier(instanceId);

        log.trace("getPrivate(instance = {}, directory = {})", instanceId, safeInstanceId);

        T provider = privateParameters.get(safeInstanceId);
        return provider != null ? provider.getPrivateParameters() : null;
    }

    public OffsetDateTime getPrivateExpiresOn(String instanceId) {
        String safeInstanceId = escapeInstanceIdentifier(instanceId);
        return privateParameters.get(safeInstanceId).getExpiresOn();
    }

    /**
     * Returns shared parameters for a given instance identifier.
     * @param instanceId the instance identifier
     * @return shared parameters or null, if no shared parameters exist for given instance identifier
     */
    public SharedParameters getShared(String instanceId) {
        String safeInstanceId = escapeInstanceIdentifier(instanceId);

        log.trace("getShared(instance = {}, directory = {})", instanceId, safeInstanceId);

        S parameters = sharedParameters.get(safeInstanceId);
        // ignore federated parameters that are expired
        if (parameters != null && parameters.getSharedParameters() != null
                && (parameters.getSharedParameters().getInstanceIdentifier().equals(instanceIdentifier)
                || parameters.getExpiresOn().isAfter(TimeUtils.offsetDateTimeNow()))) {
            return parameters.getSharedParameters();
        }
        return null;
    }

    /**
     * @return all known shared parameters
     */
    public List<SharedParameters> getShared() {
        OffsetDateTime now = TimeUtils.offsetDateTimeNow();
        return sharedParameters.values()
                .stream()
                .filter(p -> p.getSharedParameters() != null
                        && p.getSharedParameters().getInstanceIdentifier().equals(instanceIdentifier) || p.getExpiresOn().isAfter(now)
                )
                .map(SharedParametersProvider::getSharedParameters)
                .collect(Collectors.toList());
    }

    public OffsetDateTime getSharedExpiresOn(String instanceId) {
        String safeInstanceId = escapeInstanceIdentifier(instanceId);
        return sharedParameters.get(safeInstanceId).getExpiresOn();
    }

    /**
     * Applies the given function to all files belonging to the configuration directory.
     * @param consumer the function instance that should be applied to
     * @throws IOException if an error occurs
     */
    private synchronized void eachFile(final Consumer<Path> consumer) throws IOException {
        getConfigurationFiles().forEach(consumer);
    }

    protected List<Path> getConfigurationFiles() throws IOException {
        return excludeMetadataAndDirs(Files.walk(path));
    }

    private List<Path> excludeMetadataAndDirs(Stream<Path> stream) {
        return stream.filter(Files::isRegularFile)
                .filter(p -> !p.toString().endsWith(ConfigurationDirectory.FILES))
                .filter(p -> !p.toString().endsWith(ConfigurationDirectory.INSTANCE_IDENTIFIER_FILE))
                .filter(p -> !p.toString().endsWith(ConfigurationDirectory.METADATA_SUFFIX))
                .collect(Collectors.toList());
    }

    /**
     * Applies the given function to all files belonging to the configuration directory.
     * @param consumer the function instance that should be applied to all files belonging to the
     *         configuration directory.
     * @throws Exception if an error occurs
     */
    public synchronized void eachFile(FileConsumer consumer) throws IOException {
        eachFile(filepath -> {
            try (InputStream is = new FileInputStream(filepath.toFile())) {
                log.trace("Processing '{}'", filepath);

                ConfigurationPartMetadata metadata;

                try {
                    metadata = getMetadata(filepath);
                } catch (IOException e) {
                    log.error("Could not open configuration file '{}' metadata: {}", filepath, e);
                    throw e;
                }

                consumer.consume(metadata, is);
            } catch (RuntimeException e) {
                log.error("Error processing configuration file '{}': {}", filepath, e);

                throw e;
            } catch (Exception e) {
                log.error("Error processing configuration file '{}': {}", filepath, e);

                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Gets the metadata for the given file.
     * @param fileName the file name
     * @return the metadata for the given file or null if metadata file does not exist.
     * @throws Exception if the metadata cannot be loaded
     */
    public static ConfigurationPartMetadata getMetadata(Path fileName) throws IOException {
        File file = new File(fileName.toString() + ConfigurationConstants.FILE_NAME_SUFFIX_METADATA);
        try (InputStream in = new FileInputStream(file)) {
            return ConfigurationPartMetadata.read(in);
        }
    }

    protected static OffsetDateTime getFileExpiresOn(Path filePath) {
        try {
            return getMetadata(filePath).getExpirationDate();
        } catch (IOException e) {
            log.error("Unable to read expiration date", e);
            return OffsetDateTime.MAX;
        }
    }

    // ------------------------------------------------------------------------

    private String loadInstanceIdentifier() {
        Path file = Paths.get(path.toString(), INSTANCE_IDENTIFIER_FILE);

        log.trace("Loading instance identifier from {}", file);

        try {
            return FileUtils.readFileToString(file.toFile(), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            log.error("Failed to read instance identifier from " + file, e);

            throw new CodedException(X_INTERNAL_ERROR,
                    "Could not read instance identifier of this security server");
        }
    }

    protected abstract void loadPrivateParameters(Path instanceDir, Map<String, T> basePrivateParameters);

    protected abstract void loadSharedParameters(Path instanceDir, Map<String, S> basePrivateParameters);

}
