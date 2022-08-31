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
 * Class for reading global configuration directory. The directory must have sub directory per instance identifier.
 * Each sub directory must contain private and/or shared parameters.
 *
 * When querying the parameters from this class, the parameters XML is checked for modifications and if the XML has
 * been modified, the parameters are reloaded from the XML.
 */
@Slf4j
public class ConfigurationDirectoryV2 implements ConfigurationDirectory {

    @Getter
    private final Path path;

    private final String instanceIdentifier;

    private final Map<String, PrivateParametersV2> privateParameters = new HashMap<>();
    private final Map<String, SharedParametersV2> sharedParameters = new HashMap<>();

    // ------------------------------------------------------------------------

    /**
     * Constructs new directory from the given path.
     * @param directoryPath the path to the directory.
     * @throws Exception if loading configuration fails
     */
    public ConfigurationDirectoryV2(String directoryPath) throws Exception {
        this.path = Paths.get(directoryPath);

        instanceIdentifier = loadInstanceIdentifier();

        // empty maps as placeholders
        loadParameters(new HashMap<>(), new HashMap<>());
    }

    /**
     * Constructs new directory from the given path using parts from provided base that have not changed.
     * @param directoryPath the path to the directory.
     * @param base existing configurationdirectory to look for reusable parameter objects
     * @throws Exception if loading configuration fails
     */
    public ConfigurationDirectoryV2(String directoryPath, ConfigurationDirectoryV2 base) throws Exception {
        this.path = Paths.get(directoryPath);

        instanceIdentifier = loadInstanceIdentifier();

        loadParameters(base.privateParameters, base.sharedParameters);
    }

    /**
     * @return the instance identifier of this configuration. The instance identifier is lazy initialized.
     */
    public String getInstanceIdentifier() {
        return instanceIdentifier;
    }

    /**
     * Reloads the configuration directory. Only files that are new or have changed, are actually loaded.
     * @throws Exception if an error occurs during reload
     */
    private void loadParameters(Map<String, PrivateParametersV2> basePrivateParams,
            Map<String, SharedParametersV2> baseSharedParams) throws Exception {
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
    public PrivateParametersV2 getPrivate(String instanceId) throws Exception {
        String safeInstanceId = escapeInstanceIdentifier(instanceId);

        log.trace("getPrivate(instance = {}, directory = {})", instanceId, safeInstanceId);

        return privateParameters.get(safeInstanceId);
    }

    /**
     * Returns shared parameters for a given instance identifier.
     * @param instanceId the instance identifier
     * @return shared parameters or null, if no shared parameters exist for given instance identifier
     * @throws Exception if an error occurs while reading parameters
     */
    public SharedParametersV2 getShared(String instanceId) throws Exception {
        String safeInstanceId = escapeInstanceIdentifier(instanceId);

        log.trace("getShared(instance = {}, directory = {})", instanceId, safeInstanceId);

        SharedParametersV2 parameters = sharedParameters.get(safeInstanceId);
        // ignore federated parameters that are expired
        if (parameters != null
                && (parameters.getInstanceIdentifier().equals(instanceIdentifier)
                    || parameters.getExpiresOn().isAfter(OffsetDateTime.now()))) {
            return parameters;
        }
        return null;
    }

    /**
     * @return all known shared parameters
     */
    public List<SharedParametersV2> getShared() {
        OffsetDateTime now = OffsetDateTime.now();
        return sharedParameters.values()
                .stream()
                .filter(p -> p.getInstanceIdentifier().equals(instanceIdentifier) || p.getExpiresOn().isAfter(now))
                .collect(Collectors.toList());
    }

    /**
     * Applies the given function to all files belonging to the configuration directory.
     * @param consumer the function instance that should be applied to
     * @throws Exception if an error occurs
     */
    private synchronized void eachFile(final Consumer<Path> consumer) throws Exception {
        getConfigurationFiles().forEach(consumer);
    }

    protected List<Path> getConfigurationFiles() throws Exception {
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
    public synchronized void eachFile(FileConsumer consumer) throws Exception {
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

    private static OffsetDateTime getFileExpiresOn(Path filePath) {
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

    private void loadPrivateParameters(Path instanceDir, Map<String, PrivateParametersV2> basePrivateParameters) {
        String instanceId = instanceDir.getFileName().toString();

        Path privateParametersPath = Paths.get(instanceDir.toString(),
                ConfigurationConstants.FILE_NAME_PRIVATE_PARAMETERS);
        if (Files.exists(privateParametersPath)) {
            try {
                log.trace("Loading private parameters from {}", privateParametersPath);

                PrivateParametersV2 existingParameters = basePrivateParameters.get(instanceId);
                PrivateParametersV2 parametersToUse;
                OffsetDateTime fileExpiresOn = getFileExpiresOn(privateParametersPath);

                if (existingParameters != null && !existingParameters.hasChanged()) {
                    log.trace("PrivateParametersV2 from {} have not changed, reusing", privateParametersPath);
                    parametersToUse = new PrivateParametersV2(existingParameters, fileExpiresOn);
                } else {
                    log.trace("Loading PrivateParametersV2 from {}", privateParametersPath);
                    parametersToUse = new PrivateParametersV2(privateParametersPath, fileExpiresOn);
                }

                privateParameters.put(instanceId, parametersToUse);
            } catch (Exception e) {
                log.error("Unable to load private parameters from {}", instanceDir, e);
            }
        } else {
            log.trace("Not loading private parameters from {}, file does not exist", privateParametersPath);
        }
    }

    private void loadSharedParameters(Path instanceDir, Map<String, SharedParametersV2> baseSharedParameters) {
        String instanceId = instanceDir.getFileName().toString();

        Path sharedParametersPath = Paths.get(instanceDir.toString(),
                ConfigurationConstants.FILE_NAME_SHARED_PARAMETERS);
        if (Files.exists(sharedParametersPath)) {
            try {
                log.trace("Loading shared parameters from {}", sharedParametersPath);

                SharedParametersV2 existingParameters = baseSharedParameters.get(instanceId);
                SharedParametersV2 parametersToUse;
                OffsetDateTime fileExpiresOn = getFileExpiresOn(sharedParametersPath);

                if (existingParameters != null && !existingParameters.hasChanged()) {
                    log.trace("SharedParametersV2 from {} have not changed, reusing", sharedParametersPath);
                    parametersToUse = new SharedParametersV2(existingParameters, fileExpiresOn);
                } else {
                    log.trace("Loading SharedParametersV2 from {}", sharedParametersPath);
                    parametersToUse = new SharedParametersV2(sharedParametersPath, fileExpiresOn);
                }

                sharedParameters.put(instanceId, parametersToUse);
            } catch (Exception e) {
                log.error("Unable to load shared parameters from {}", instanceDir, e);
            }
        } else {
            log.trace("Not loading shared parameters from {}, file does not exist", sharedParametersPath);
        }
    }

}
