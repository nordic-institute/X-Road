/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
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
import ee.ria.xroad.common.SystemProperties;

import lombok.extern.slf4j.Slf4j;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.ErrorCodes.X_INVALID_XML;
import static ee.ria.xroad.common.conf.globalconf.VersionedConfigurationDirectory.isCurrentVersion;

/**
 * Configuration client downloads the configuration from sources found in the configuration anchor.
 */
@Slf4j
class ConfigurationClient {
    private final String globalConfigurationDir;

    private final ConfigurationDownloader downloader;

    private ConfigurationSource configurationAnchor;

    ConfigurationClient(String globalConfigurationDir, int configurationVersion) {
        this.globalConfigurationDir = globalConfigurationDir;
        downloader = new ConfigurationDownloader(globalConfigurationDir, configurationVersion);
    }

    ConfigurationClient(String globalConfigurationDir) {
        this.globalConfigurationDir = globalConfigurationDir;
        downloader = new ConfigurationDownloader(globalConfigurationDir);
    }

    ConfigurationClient(String globalConfigurationDir, ConfigurationDownloader downloader, ConfigurationSource configurationAnchor) {
        this.globalConfigurationDir = globalConfigurationDir;
        this.downloader = downloader;
        this.configurationAnchor = configurationAnchor;
    }

    synchronized void execute() throws Exception {
        log.debug("Configuration client executing...");

        if (configurationAnchor == null || configurationAnchor.hasChanged()) {
            log.debug("Initializing configuration anchor");

            initConfigurationAnchor();
        }

        downloadConfigurationFromAnchor();
        List<ConfigurationSource> configurationSources = getAdditionalConfigurationSources();

        FederationConfigurationSourceFilter sourceFilter =
                new FederationConfigurationSourceFilterImpl(configurationAnchor.getInstanceIdentifier());

        deleteExtraConfigurationDirectories(configurationSources, sourceFilter);

        downloadConfigurationFromAdditionalSources(configurationSources, sourceFilter);
    }

    protected List<ConfigurationSource> getAdditionalConfigurationSources() {
        PrivateParameters privateParameters = loadPrivateParameters();
        return privateParameters != null ? privateParameters.getConfigurationAnchors() : List.of();
    }

    private void initConfigurationAnchor() throws Exception {
        log.trace("initConfigurationAnchor()");

        String anchorFileName = SystemProperties.getConfigurationAnchorFile();
        if (!Files.exists(Paths.get(anchorFileName))) {
            log.warn("Cannot download configuration, anchor file {} does not exist", anchorFileName);

            throw new FileNotFoundException(anchorFileName);
        }

        try {
            configurationAnchor = new ConfigurationAnchor(anchorFileName);
        } catch (Exception e) {
            String message = String.format("Failed to load configuration anchor from file %s", anchorFileName);

            log.error(message, e);

            throw new CodedException(X_INVALID_XML, message);
        }

        saveInstanceIdentifier();

    }

    void saveInstanceIdentifier() throws Exception {
        ConfigurationDirectory.saveInstanceIdentifier(globalConfigurationDir,
                configurationAnchor.getInstanceIdentifier());
    }

    private void downloadConfigurationFromAnchor() throws Exception {
        log.debug("downloadConfFromAnchor()");

        handleResult(downloader.download(configurationAnchor), true);
    }

    private PrivateParameters loadPrivateParameters() {
        try {
            Path privateParamsPath = Path.of(globalConfigurationDir, configurationAnchor.getInstanceIdentifier(),
                    ConfigurationConstants.FILE_NAME_PRIVATE_PARAMETERS);

            if (!Files.exists(privateParamsPath)) {
                log.debug("Skipping reading private parameters as {} does not exist", privateParamsPath);
                return null;
            }

            PrivateParametersProvider p = isCurrentVersion(privateParamsPath)
                    ? new PrivateParametersV3(privateParamsPath, OffsetDateTime.MAX)
                    : new PrivateParametersV2(privateParamsPath, OffsetDateTime.MAX);
            return p.getPrivateParameters();
        } catch (Exception e) {
            log.error("Failed to read additional configuration sources from" + globalConfigurationDir, e);
            return null;
        }
    }

    protected void deleteExtraConfigurationDirectories(List<ConfigurationSource> configurationSources,
                                                     FederationConfigurationSourceFilter sourceFilter) {
        Set<String> directoriesToKeep;
        if (configurationSources != null) {
            directoriesToKeep = configurationSources.stream()
                    .map(ConfigurationSource::getInstanceIdentifier)
                    .filter(sourceFilter::shouldDownloadConfigurationFor)
                    .map(ConfigurationUtils::escapeInstanceIdentifier)
                    .collect(Collectors.toSet());

        } else {
            directoriesToKeep = new HashSet<>();
        }

        // always keep main instance directory
        directoriesToKeep.add(ConfigurationUtils.escapeInstanceIdentifier(configurationAnchor.getInstanceIdentifier()));

        // delete all additional configurations no longer referenced in anchor
        ConfigurationDirectory.deleteExtraDirs(globalConfigurationDir, directoriesToKeep);
    }

    private void downloadConfigurationFromAdditionalSources(List<ConfigurationSource> configurationSources,
                                                FederationConfigurationSourceFilter sourceFilter) throws Exception {
        if (configurationSources != null) {
            for (ConfigurationSource source : configurationSources) {
                if (sourceFilter.shouldDownloadConfigurationFor(source.getInstanceIdentifier())) {
                    DownloadResult result = downloader.download(source, ConfigurationConstants.CONTENT_ID_SHARED_PARAMETERS);
                    handleResult(result, false);
                }
            }
        }
    }

    private void handleResult(DownloadResult result, boolean throwIfFailure) throws Exception {
        if (!result.isSuccess()) {
            try {
                handleFailure(result);
            } catch (Exception e) {
                if (throwIfFailure) {
                    throw e; // re-throw
                }
            }
        } else {
            log.info("Successfully downloaded configuration from: {}", result.getConfiguration().getLocation().getDownloadURL());
        }
    }

    private void handleFailure(DownloadResult result) throws Exception {
        final StringBuilder errorMessage = new StringBuilder(
                "Failed to download configuration from any configuration location:\n");

        result.getExceptions().forEach((l, e) -> {
            errorMessage.append("\tlocation: ");
            errorMessage.append(l.getDownloadURL());
            errorMessage.append("; error: ");
            errorMessage.append(e.toString());
            errorMessage.append("\n");
        });

        log.error(errorMessage.toString());

        Exception lastException = result.getExceptions().values().stream()
                .reduce((a, b) -> b).orElse(null);

        if (lastException != null) {
            throw lastException;
        }
    }
}
