package ee.cyber.sdsb.common.conf.globalconf;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.io.FileUtils;
import org.joda.time.DateTime;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.conf.ConfProvider;
import ee.cyber.sdsb.common.util.AtomicSave;

import static ee.cyber.sdsb.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.cyber.sdsb.common.ErrorCodes.X_OUTDATED_GLOBALCONF;
import static ee.cyber.sdsb.common.conf.globalconf.ConfigurationUtils.escapeInstanceIdentifier;

/**
 * Class for reading global configuration directory. The directory must
 * have sub directory per instance identifier. Each sub directory must contain
 * private and/or shared parameters.
 *
 * When querying the parameters from this class, the parameters XML is checked
 * for modifications and if the XML has been modified, the parameters are
 * reloaded from the XML.
 */
@Slf4j
public class ConfigurationDirectory {

    /**
     * Interface of the function that can be applied to files belonging to
     * the configuration directory.
     */
    @FunctionalInterface
    public interface FileConsumer {

        /**
         * Applies to files belonging to the configuration directory.
         * @param metadata the metadata of the file
         * @param contents the contents of the file
         * @throws Exception if an error occurs
         */
        void consume(ConfigurationPartMetadata metadata, InputStream contents)
                throws Exception;
    }

    // ------------------------------------------------------------------------

    public static final String PRIVATE_PARAMETERS_XML = "private-params.xml";
    public static final String SHARED_PARAMETERS_XML = "shared-params.xml";

    private static final String METADATA_SUFFIX = ".metadata";
    private static final String INSTANCE_IDENTIFIER_FILE =
            "instance-identifier";

    @Getter
    private final Path path;
    private final boolean reloadIfChanged;

    private String instanceIdentifier;

    private Map<String, PrivateParameters> privateParameters = new HashMap<>();
    private Map<String, SharedParameters> sharedParameters = new HashMap<>();

    // ------------------------------------------------------------------------

    /**
     * Constructs new directory from the given path.
     * @param directoryPath the path to the directory.
     * @throws Exception if loading configuration fails
     */
    public ConfigurationDirectory(String directoryPath) throws Exception {
        this(directoryPath, false);
    }

    /**
     * Constructs new directory from the given path.
     * @param directoryPath the path to the directory.
     * @param reloadIfChanged if true, automatic reload and detection of
     * parameters is performed.
     * @throws Exception if loading configuration fails
     */
    public ConfigurationDirectory(String directoryPath,
            boolean reloadIfChanged) throws Exception {
        this.path = Paths.get(directoryPath);
        this.reloadIfChanged = reloadIfChanged;
        reload();
    }

    /**
     * @return the instance identifier of this configuration. The instance
     * identifier is lazy initialized.
     */
    public synchronized String getInstanceIdentifier() {
        if (instanceIdentifier == null) {
            loadInstanceIdentifier();
        }

        return instanceIdentifier;
    }

    /**
     * Reloads the configuration directory. Only files that are new or have
     * changed, are actually loaded.
     * @throws Exception if an error occurs during reload
     */
    public synchronized void reload() throws Exception {
        Map<String, PrivateParameters> privateParams = new HashMap<>();
        Map<String, SharedParameters> sharedParams = new HashMap<>();

        log.trace("Reloading configuration from {}", path);

        instanceIdentifier = null;

        try (DirectoryStream<Path> stream =
                Files.newDirectoryStream(path, Files::isDirectory)) {
            for (Path instanceDir : stream) {
                log.trace("Loading parameters from {}", instanceDir);

                loadPrivateParameters(instanceDir, privateParams);
                loadSharedParameters(instanceDir, sharedParams);
            }
        }

        this.privateParameters = privateParams;
        this.sharedParameters = sharedParams;
    }

    /**
     * Returns private parameters for a given instance identifier.
     * @param instanceId the instance identifier
     * @return private parameters or null, if no private parameters exist for
     * given instance identifier
     * @throws Exception if an error occurs while reading parameters
     */
    public synchronized PrivateParameters getPrivate(String instanceId)
            throws Exception {
        String safeInstanceId = escapeInstanceIdentifier(instanceId);

        log.trace("getPrivate(instance = {}, directory = {})",
                instanceId, safeInstanceId);

        PrivateParameters parameters = privateParameters.get(safeInstanceId);
        if (!reloadIfChanged) {
            return parameters;
        }

        if (parameters != null && parameters.hasChanged()) {
            parameters.reload();
        } else if (parameters == null) {
            // Parameters not cached, attempt to load it from disk.
            Path instanceDir = Paths.get(path.toString(), safeInstanceId);
            loadPrivateParameters(instanceDir, privateParameters);
        }

        return privateParameters.get(safeInstanceId);
    }

    /**
     * Returns shared parameters for a given instance identifier.
     * @param instanceId the instance identifier
     * @return shared parameters or null, if no shared parameters exist for
     * given instance identifier
     * @throws Exception if an error occurs while reading parameters
     */
    public synchronized SharedParameters getShared(String instanceId)
            throws Exception {
        String safeInstanceId = escapeInstanceIdentifier(instanceId);

        log.trace("getShared(instance = {}, directory = {})",
                instanceId, safeInstanceId);

        SharedParameters parameters = sharedParameters.get(safeInstanceId);
        if (!reloadIfChanged) {
            return parameters;
        }

        if (parameters != null && parameters.hasChanged()) {
            parameters.reload();
        } else if (parameters == null) {
            // Parameters not cached, attempt to load it from disk.
            Path instanceDir = Paths.get(path.toString(), safeInstanceId);
            loadSharedParameters(instanceDir, sharedParameters);
        }

        return sharedParameters.get(safeInstanceId);
    }

    /**
     * @return all known shared parameters
     */
    public synchronized List<SharedParameters> getShared() {
        return new ArrayList<>(sharedParameters.values());
    }

    /**
     * Applies the given function to all files belonging to
     * the configuration directory.
     * @param consumer the function instance that should be applied to
     * @throws Exception if an error occurs
     */
    public synchronized void eachFile(Consumer<Path> consumer)
            throws Exception {
        try (Stream<Path> paths = excludeMetadataAndDirs(Files.walk(path))) {
            paths.forEach(consumer::accept);
        }
    }

    /**
     * Applies the given function to all files belonging to
     * the configuration directory.
     * @param consumer the function instance that should be applied to
     * all files belonging to the configuration directory.
     * @throws Exception if an error occurs
     */
    public synchronized void eachFile(FileConsumer consumer) throws Exception {
        try (Stream<Path> paths = excludeMetadataAndDirs(Files.walk(path))) {
            for (Path filepath : paths.collect(Collectors.toList())) {
                log.trace("Processing '{}'", filepath);
                try (InputStream is = new FileInputStream(filepath.toFile())) {
                    ConfigurationPartMetadata metadata = null;
                    try {
                        metadata = getMetadata(filepath);
                    } catch (Exception e) {
                        log.error("Could not open configuration file '{}'"
                                + " metadata: {}", filepath, e);
                        throw e;
                    }
                    consumer.consume(metadata, is);
                } catch (Exception e) {
                    log.error("Error processing configuration file '{}': {}",
                            filepath, e);
                    throw e;
                }
            }
        }
    }

    /**
     * Returns true, if the file has expired. Returns false, if the file has
     * not expired or the expiration time-stamp cannot be found.
     * @param fileName the absolute file name
     * @return true, if the file has expired
     */
    public static final boolean isExpired(Path fileName) {
        try {
            DateTime expiresOn = getMetadata(fileName).getExpirationDate();
            if (expiresOn.isBeforeNow()) {
                log.info("{} expired on {}", fileName, expiresOn);
                return true;
            }
        } catch (Exception e) {
            log.error("Failed to get expiration date of file " + fileName, e);
        }

        return false;
    }

    /**
     * Throws exception with error code ErrorCodes.X_OUTDATED_GLOBALCONF if the
     * file is too old.
     * @param fileName the file name
     */
    public static final void verifyUpToDate(Path fileName) {
        if (isExpired(fileName)) {
            throw new CodedException(X_OUTDATED_GLOBALCONF, "%s is too old",
                    fileName);
        }
    }

    /**
     * Saves the file to disk along with corresponding expiration date file.
     * @param fileName the name of the file to save
     * @param content the content of the file
     * @param expirationDate the file expiration date
     * @throws Exception if an error occurs
     */
    public static final void save(Path fileName, byte[] content,
            ConfigurationPartMetadata expirationDate) throws Exception {
        if (fileName == null) {
            return;
        }

        Path parent = fileName.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        log.info("Saving content to file {}", fileName);

        // save the content to disk
        AtomicSave.execute(fileName.toString(), "conf", content);

        // save the content metadata date to disk
        saveMetadata(fileName, expirationDate);
    }

    /**
     * Saves the expiration date for the given file.
     * @param fileName the file
     * @param metadata the metadata
     * @throws Exception if an error occurs
     */
    public static final void saveMetadata(Path fileName,
            ConfigurationPartMetadata metadata) throws Exception {
        AtomicSave.execute(fileName.toString() + METADATA_SUFFIX, "expires",
                metadata.toByteArray());
    }

    /**
     * Deletes the file and accompanying expire date.
     * @param fileName the file name
     */
    public static final void delete(String fileName) {
        File file = new File(fileName);
        if (!file.delete()) {
            log.error("Failed to delete file {}", file);
        }

        File metadataFile = new File(fileName + METADATA_SUFFIX);
        if (!metadataFile.delete()) {
            log.error("Failed to delete file {}", metadataFile);
        }

        File directory = file.getParentFile();
        if (directory.isDirectory()) {
            directory.delete(); // no need to check for return value
        }
    }

    /**
     * Saves the instance identifier of this security server to file.
     * @param confPath path to the configuration
     * @param instanceIdentifier the instance identifier
     * @throws Exception if saving instance identifier fails
     */
    public static final void saveInstanceIdentifier(String confPath,
            String instanceIdentifier) throws Exception {
        Path file = Paths.get(confPath, INSTANCE_IDENTIFIER_FILE);

        log.trace("Saving instance identifier to {}", file);

        AtomicSave.execute(file.toString(), "inst",
                instanceIdentifier.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @param fileName the file name
     * @return the metadata for the given file.
     * @throws Exception if the metadata cannot be loaded
     */
    public static ConfigurationPartMetadata getMetadata(Path fileName)
            throws Exception {
        File file = new File(fileName.toString() + METADATA_SUFFIX);
        try (InputStream in = new FileInputStream(file)) {
            return ConfigurationPartMetadata.read(in);
        }
    }

    // ------------------------------------------------------------------------

    private void loadInstanceIdentifier() {
        Path file = Paths.get(path.toString(), INSTANCE_IDENTIFIER_FILE);

        log.trace("Loading instance identifier from {}", file);
        try {
            instanceIdentifier =
                    FileUtils.readFileToString(file.toFile()).trim();
        } catch (Exception e) {
            log.error("Failed to read instance identifier from " + file, e);
            throw new CodedException(X_INTERNAL_ERROR,
                    "Could not read instance identifier of "
                            + "this security server");
        }
    }

    private void loadPrivateParameters(Path instanceDir,
            Map<String, PrivateParameters> privateParams)
                    throws Exception {
        String instanceId = instanceDir.getFileName().toString();

        Path privateParametersPath =
                Paths.get(instanceDir.toString(), PRIVATE_PARAMETERS_XML);

        if (Files.exists(privateParametersPath)) {
            log.trace("Loading private parameters from {}",
                    privateParametersPath);

            privateParams.put(instanceId,
                    loadParameters(privateParametersPath,
                            PrivateParameters.class,
                            this.privateParameters.get(instanceId)));
        } else {
            log.trace("Not loading private parameters from {}, "
                    + "file does not exist", privateParametersPath);
        }
    }

    private void loadSharedParameters(Path instanceDir,
            Map<String, SharedParameters> sharedParams) throws Exception {
        String instanceId = instanceDir.getFileName().toString();

        Path sharedParametersPath =
                Paths.get(instanceDir.toString(), SHARED_PARAMETERS_XML);
        if (Files.exists(sharedParametersPath)) {
            log.trace("Loading shared parameters from {}",
                    sharedParametersPath);

            sharedParams.put(instanceId,
                    loadParameters(sharedParametersPath,
                            SharedParameters.class,
                            this.sharedParameters.get(instanceId)));
        } else {
            log.trace("Not loading shared parameters from {}, "
                    + "file does not exist", sharedParametersPath);
        }
    }

    // Loads the parameters from file if the file has changed.
    // Returns the parameters or null if the file does not exist.
    private static <T extends ConfProvider> T loadParameters(Path path,
            Class<T> clazz, T existingInstance) throws Exception {
        T params = existingInstance != null
                ? existingInstance : (T) clazz.newInstance();

        if (params.hasChanged()) {
            log.trace("Loading {} from {}", clazz.getSimpleName(), path);

            params.load(path.toString());
        }

        return params;
    }

    private static Stream<Path> excludeMetadataAndDirs(Stream<Path> stream) {
        return stream.filter(Files::isRegularFile)
                .filter(p -> !p.endsWith(INSTANCE_IDENTIFIER_FILE))
                .filter(p -> !p.toString().endsWith(METADATA_SUFFIX));
    }
}
