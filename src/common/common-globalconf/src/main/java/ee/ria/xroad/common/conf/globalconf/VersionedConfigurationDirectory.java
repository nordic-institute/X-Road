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

import javax.annotation.concurrent.Immutable;

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
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.SystemProperties.CURRENT_GLOBAL_CONFIGURATION_VERSION;
import static ee.ria.xroad.common.conf.globalconf.ConfigurationUtils.escapeInstanceIdentifier;

/**
 * Class for reading global configuration directory. The directory must have subdirectory per instance identifier.
 * Each subdirectory must contain private and/or shared parameters.
 * <br/> When querying the parameters from this class, the parameters XML is checked for modifications and if the XML has
 * been modified, the parameters are reloaded from the XML.
 *
 */
@Slf4j
@Immutable
public class VersionedConfigurationDirectory implements ConfigurationDirectory {

    @Getter
    private final Path path;

    @Getter
    private final String instanceIdentifier;

    protected final Map<String, PrivateParametersProvider> privateParameters;
    protected final Map<String, SharedParametersProvider> sharedParameters;

    // ------------------------------------------------------------------------

    /**
     * Constructs new directory from the given path.
     * @param directoryPath the path to the directory.
     * @throws IOException if loading configuration fails
     */
    public VersionedConfigurationDirectory(String directoryPath) throws IOException {
        this.path = Paths.get(directoryPath);

        instanceIdentifier = loadInstanceIdentifier();

        // empty maps as placeholders
        privateParameters = Map.copyOf(loadPrivateParameters(new HashMap<>()));
        sharedParameters = Map.copyOf(loadSharedParameters(new HashMap<>()));
    }

    /**
     * Constructs new directory from the given path using parts from provided base that have not changed.
     * @param directoryPath the path to the directory.
     * @param base existing configuration directory to look for reusable parameter objects
     * @throws IOException if loading configuration fails
     */
    public VersionedConfigurationDirectory(String directoryPath, VersionedConfigurationDirectory base) throws IOException {
        this.path = Paths.get(directoryPath);

        instanceIdentifier = loadInstanceIdentifier();

        privateParameters = Map.copyOf(loadPrivateParameters(base.privateParameters));
        sharedParameters = Map.copyOf(loadSharedParameters(base.sharedParameters));
    }

    /**
     * Reloads private parameters. Only files that are new or have changed, are actually loaded.
     * @throws IOException if an error occurs during reload
     */
    private Map<String, PrivateParametersProvider> loadPrivateParameters(Map<String, PrivateParametersProvider> basePrivateParams)
            throws IOException {
        log.trace("Loading PrivateParameters from {}", path);

        Map<String, PrivateParametersProvider> privateParams = new HashMap<>(basePrivateParams);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path, Files::isDirectory)) {
            for (Path instanceDir : stream) {
                log.trace("LoadingPrivateParameters from {}", instanceDir);
                loadPrivateParameters(instanceDir, privateParams);
            }
        }
        return privateParams;
    }

    private void loadPrivateParameters(Path instanceDir, Map<String, PrivateParametersProvider> basePrivateParams) {
        String instanceId = instanceDir.getFileName().toString();
        Path privateParametersPath = Paths.get(instanceDir.toString(), ConfigurationConstants.FILE_NAME_PRIVATE_PARAMETERS);
        if (Files.exists(privateParametersPath)) {
            try {
                PrivateParametersProvider existingParameters = basePrivateParams.get(instanceId);
                OffsetDateTime fileExpiresOn = getFileExpiresOn(privateParametersPath);
                PrivateParametersProvider parametersToUse;
                if (existingParameters != null && !existingParameters.hasChanged()) {
                    log.trace("PrivateParameters from {} have not changed, reusing", privateParametersPath);
                    parametersToUse = existingParameters.refresh(fileExpiresOn);
                } else {
                    log.trace("Reloading PrivateParameters from {} ", privateParametersPath);
                    parametersToUse = isCurrentVersion(privateParametersPath)
                            ? new PrivateParametersV3(privateParametersPath, fileExpiresOn)
                            : new PrivateParametersV2(privateParametersPath, fileExpiresOn);
                }
                basePrivateParams.put(instanceId, parametersToUse);
            } catch (Exception e) {
                log.error("Unable to load PrivateParameters from {}", instanceDir, e);
            }
        } else {
            log.trace("Not loading PrivateParameters from {}, file does not exist", privateParametersPath);
        }
    }

    /**
     * Reloads shared parameters. Only files that are new or have changed, are actually loaded.
     * @throws IOException if an error occurs during reload
     */
    private Map<String, SharedParametersProvider> loadSharedParameters(Map<String, SharedParametersProvider> baseSharedParams)
            throws IOException {
        log.trace("Loading SharedParameters from {}", path);

        Map<String, SharedParametersProvider> sharedParams = new HashMap<>(baseSharedParams);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path, Files::isDirectory)) {
            for (Path instanceDir : stream) {
                log.trace("Loading SharedParameters from {}", instanceDir);
                loadSharedParameters(instanceDir, sharedParams);
            }
        }
        return sharedParams;
    }

    private void loadSharedParameters(Path instanceDir, Map<String, SharedParametersProvider> baseSharedParams) {
        String instanceId = instanceDir.getFileName().toString();
        Path sharedParametersPath = Paths.get(instanceDir.toString(),
                ConfigurationConstants.FILE_NAME_SHARED_PARAMETERS);
        if (Files.exists(sharedParametersPath)) {
            try {
                SharedParametersProvider existingParameters = baseSharedParams.get(instanceId);
                OffsetDateTime fileExpiresOn = getFileExpiresOn(sharedParametersPath);
                SharedParametersProvider parametersToUse;
                if (existingParameters != null && !existingParameters.hasChanged()) {
                    log.trace("SharedParameters from {} have not changed, reusing", sharedParametersPath);
                    parametersToUse = existingParameters.refresh(fileExpiresOn);
                } else {
                    log.trace("Reloading SharedParameters from {} ", sharedParametersPath);
                    parametersToUse = isCurrentVersion(sharedParametersPath)
                            ? new SharedParametersV3(sharedParametersPath, fileExpiresOn)
                            : new SharedParametersV2(sharedParametersPath, fileExpiresOn);
                }
                baseSharedParams.put(instanceId, parametersToUse);
            } catch (Exception e) {
                log.error("Unable to load SharedParameters from {}", instanceDir, e);
            }
        } else {
            log.trace("Not loading SharedParameters from {}, file does not exist", sharedParametersPath);
        }
    }

    /**
     * Returns private parameters for a given instance identifier.
     * @param instanceId the instance identifier
     * @return optional of private parameters or {@link Optional#empty()} if no private parameters exist for given instance identifier
     */
    public Optional<PrivateParameters> findPrivate(String instanceId) {
        String safeInstanceId = escapeInstanceIdentifier(instanceId);

        log.trace("findPrivate(instance = {}, directory = {})", instanceId, safeInstanceId);

        PrivateParametersProvider provider = privateParameters.get(safeInstanceId);
        return Optional.ofNullable(provider)
                .map(PrivateParametersProvider::getPrivateParameters);
    }

    /**
     * Returns shared parameters for a given instance identifier.
     * @param instanceId the instance identifier
     * @return optional of shared parameters or {@link Optional#empty()} if no shared parameters exist for given instance identifier
     */
    public Optional<SharedParameters> findShared(String instanceId) {
        String safeInstanceId = escapeInstanceIdentifier(instanceId);

        log.trace("findShared(instance = {}, directory = {})", instanceId, safeInstanceId);

        Predicate<SharedParametersProvider> isMainInstance = params ->
                params.getSharedParameters() != null && instanceIdentifier.equals(params.getSharedParameters().getInstanceIdentifier());
        Predicate<SharedParametersProvider> notExpired = params ->
                params.getExpiresOn().isAfter(TimeUtils.offsetDateTimeNow());

        SharedParametersProvider provider = sharedParameters.get(safeInstanceId);
        return Optional.ofNullable(provider)
                .filter(isMainInstance.or(notExpired))
                .map(SharedParametersProvider::getSharedParameters);
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
                .toList();
    }

    public boolean isExpired() {
        OffsetDateTime now = TimeUtils.offsetDateTimeNow();
        String safeInstanceId = escapeInstanceIdentifier(instanceIdentifier);

        OffsetDateTime privateExpiresOn = privateParameters.get(safeInstanceId).getExpiresOn();
        if (now.isAfter(privateExpiresOn)) {
            log.warn("Main privateParameters expired at {}", privateExpiresOn);
            return true;
        }
        OffsetDateTime sharedExpiresOn = sharedParameters.get(safeInstanceId).getExpiresOn();
        if (now.isAfter(sharedExpiresOn)) {
            log.warn("Main sharedParameters expired at {}", sharedExpiresOn);
            return true;
        }
        return false;
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
                .toList();
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

    public static boolean isCurrentVersion(Path filePath) {
        Integer confVersion = getVersion(filePath);
        return confVersion != null && confVersion == CURRENT_GLOBAL_CONFIGURATION_VERSION;
    }

    public static Integer getVersion(Path filePath) {
        try {
            String version = getMetadata(filePath).getConfigurationVersion();
            return Integer.parseInt(version);
        } catch (IOException | NumberFormatException e) {
            log.error("Unable to read configuration version", e);
            return null;
        }
    }

    protected static OffsetDateTime getFileExpiresOn(Path filePath) {
        try {
            OffsetDateTime expiresOn = getMetadata(filePath).getExpirationDate();
            return expiresOn != null ? expiresOn : OffsetDateTime.MAX;
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

}
