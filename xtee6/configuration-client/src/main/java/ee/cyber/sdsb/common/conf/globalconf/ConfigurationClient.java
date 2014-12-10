package ee.cyber.sdsb.common.conf.globalconf;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.SystemProperties;

import static ee.cyber.sdsb.common.ErrorCodes.X_INVALID_XML;

/**
 * Configuration client downloads the configuration from sources found in the
 * configuration anchor.
 */
@Slf4j
@RequiredArgsConstructor
@AllArgsConstructor
public class ConfigurationClient {

    private final Map<String, Set<ConfigurationSource>> additionalSources =
            new HashMap<>();

    private final DownloadedFiles downloadedFiles;
    private final Configuration configuration;

    private ConfigurationAnchor configurationAnchor;

    /**
     * Executes the download sequence.
     * @throws Exception if an error occurs
     */
    public synchronized void execute() throws Exception {
        log.trace("Configuration client executing...");

        if (configurationAnchor == null || configurationAnchor.hasChanged()) {
            initConfigurationAnchor();
        }

        try {
            downloadConfigurationFromAnchor();

            if (!additionalSources.isEmpty()) {
                downloadConfigurationFromAdditionalSources();
            }
        } finally {
            try {
                downloadedFiles.sync();
            } catch (Exception e) {
                log.error("Failed to sync downloaded files list", e);
            }
        }
    }

    private void initConfigurationAnchor() throws Exception {
        log.trace("initConfigurationAnchor()");

        String anchorFileName = SystemProperties.getConfigurationAnchorFile();
        if (!Files.exists(Paths.get(anchorFileName))) {
            log.warn("Cannot download configuration, anchor file {} does not "
                    + "exist", anchorFileName);
            throw new FileNotFoundException(anchorFileName);
        }

        try {
            configurationAnchor = new ConfigurationAnchor(anchorFileName);
        } catch (Exception e) {
            log.error("Failed to load configuration anchor from file "
                    + anchorFileName, e);
            throw new CodedException(X_INVALID_XML,
                    "Failed to load configuration anchor from file %s",
                    anchorFileName);
        }

        saveInstanceIdentifier();

        initAdditionalConfigurationSources();
    }

    void saveInstanceIdentifier() throws Exception {
        ConfigurationDirectory.saveInstanceIdentifier(
                SystemProperties.getConfigurationPath(),
                configurationAnchor.getInstanceIdentifier());
    }

    void initAdditionalConfigurationSources() {
        log.trace("initAdditionalConfigurationSources()");

        additionalSources.clear();

        String confDir = SystemProperties.getConfigurationPath();
        try {
            ConfigurationDirectory dir = new ConfigurationDirectory(confDir);
            PrivateParameters privateParameters =
                    dir.getPrivate(configurationAnchor.getInstanceIdentifier());
            if (privateParameters != null) {
                putAdditionalConfigurationSources(
                        privateParameters.getInstanceIdentifier(),
                        privateParameters.getConfigurationSource());
            }
        } catch (Exception e) {
            log.error("Failed to initialize configuration directory "
                    + confDir, e);
        }
    }

    private void downloadConfigurationFromAnchor() throws Exception {
        log.trace("downloadConfFromAnchor()");

        DownloadResult result = configuration.download(configurationAnchor);
        if (result.isSuccess()) {
            handleSuccess(result);
        } else {
            handleFailure(result);
        }

        configuration.getAdditionalSources().forEach((k, v) -> {
            putAdditionalConfigurationSources(k, v);
        });
    }

    private void downloadConfigurationFromAdditionalSources() throws Exception {
        log.trace("Downloading configuration from additional sources ({})",
                additionalSources.size());

        for (Set<ConfigurationSource> sources : additionalSources.values()) {
            for (ConfigurationSource source : sources) {
                DownloadResult result =
                        configuration.download(source,
                                SharedParameters.CONTENT_ID_SHARED_PARAMETERS);
                if (result.isSuccess()) {
                    handleSuccess(result);
                } else {
                    try {
                        handleFailure(result);
                    } catch (Exception e) {
                        // Only fatal, if the instance is this server's instance
                        if (source.getInstanceIdentifier().equals(
                                configurationAnchor.getInstanceIdentifier())) {
                            throw e; // re-throw
                        }
                    }
                }
            }
        }
    }

    private void handleSuccess(DownloadResult result) {
        log.trace("handleSuccess()");

        downloadedFiles.add(result.getFiles());
    }

    private void handleFailure(DownloadResult result) throws Exception {
        log.error("Failed to download configuration from "
                + "any configuration location");
        result.getExceptions().forEach((l, e) -> {
            log.error("{}: {}", l.getDownloadURL(), e.toString());
        });

        Exception lastException = result.getExceptions().values().stream()
                .reduce((a, b) -> b).orElse(null);
        if (lastException != null) {
            throw lastException;
        }
    }

    private void putAdditionalConfigurationSources(String instanceIdentifier,
            Collection<ConfigurationSource> sources) {
        additionalSources.put(instanceIdentifier, new HashSet<>(sources));
    }
}
