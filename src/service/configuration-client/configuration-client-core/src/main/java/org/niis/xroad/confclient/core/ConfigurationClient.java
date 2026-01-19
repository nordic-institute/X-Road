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

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.annotation.ArchUnitSuppressed;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.confclient.core.globalconf.ConfigurationAnchorProvider;
import org.niis.xroad.globalconf.model.ConfigurationAnchor;
import org.niis.xroad.globalconf.model.ConfigurationConstants;
import org.niis.xroad.globalconf.model.ConfigurationDirectory;
import org.niis.xroad.globalconf.model.ConfigurationSource;
import org.niis.xroad.globalconf.model.ConfigurationUtils;
import org.niis.xroad.globalconf.model.ParametersProviderFactory;
import org.niis.xroad.globalconf.model.PrivateParameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.niis.xroad.common.core.exception.ErrorCode.ANCHOR_FILE_NOT_FOUND;
import static org.niis.xroad.common.core.exception.ErrorCode.MALFORMED_ANCHOR;
import static org.niis.xroad.globalconf.model.VersionedConfigurationDirectory.getVersion;

/**
 * Configuration client downloads the configuration from sources found in the configuration anchor.
 */
@Slf4j
@ArchUnitSuppressed("NoVanillaExceptions")
public class ConfigurationClient {
    private final ConfigurationAnchorProvider configurationAnchorProvider;
    private final String globalConfigurationDir;
    private final String allowedFederations;

    private final ConfigurationDownloader downloader;

    private ConfigurationSource configurationAnchor;

    @Getter
    private String lastSuccessfulLocationUrl = "";

    public ConfigurationClient(ConfigurationAnchorProvider configurationAnchorProvider, String globalConfigurationDir,
                               ConfigurationDownloader downloader, String allowedFederations) {
        this(configurationAnchorProvider, globalConfigurationDir, downloader, null, allowedFederations);
    }

    ConfigurationClient(ConfigurationAnchorProvider configurationAnchorProvider, String globalConfigurationDir,
                        ConfigurationDownloader downloader,
                        ConfigurationSource configurationAnchor, String allowedFederations) {
        this.configurationAnchorProvider = configurationAnchorProvider;
        this.globalConfigurationDir = globalConfigurationDir;
        this.downloader = downloader;
        this.configurationAnchor = configurationAnchor;
        this.allowedFederations = allowedFederations;
    }

    public synchronized void execute() throws Exception {
        log.debug("Configuration client executing...");

        if (configurationAnchor == null) {
            log.debug("Initializing configuration anchor");

            initConfigurationAnchor();
        }

        DownloadResult downloadResult = downloadConfigurationFromAnchor();
        lastSuccessfulLocationUrl = downloadResult.getLastSuccessfulLocationUrl();
        var configurationSources = getAdditionalConfigurationSources();

        FederationConfigurationSourceFilter sourceFilter =
                new FederationConfigurationSourceFilter(configurationAnchor.getInstanceIdentifier(), allowedFederations);

        deleteExtraConfigurationDirectories(configurationSources, sourceFilter);

        downloadConfigurationFromAdditionalSources(configurationSources, sourceFilter);
    }

    protected List<PrivateParameters.ConfigurationAnchor> getAdditionalConfigurationSources() {
        PrivateParameters privateParameters = loadPrivateParameters();
        return privateParameters != null ? privateParameters.getConfigurationAnchors() : List.of();
    }

    private void initConfigurationAnchor() {
        log.trace("initConfigurationAnchor()");

        if (configurationAnchorProvider == null || !configurationAnchorProvider.isAnchorPresent()) {
            String warningMessage;
            if (configurationAnchorProvider == null) {
                warningMessage = "Cannot download configuration, no configuration anchor present.";
            } else {
                warningMessage = "Cannot download configuration, configuration anchor does not exist (%s)"
                        .formatted(configurationAnchorProvider.source());
            }
            log.warn(warningMessage);
            throw XrdRuntimeException.systemException(ANCHOR_FILE_NOT_FOUND)
                    .details(warningMessage)
                    .build();
        }

        try {
            configurationAnchor = new ConfigurationAnchor(configurationAnchorProvider.get().get());
        } catch (Exception e) {
            String message = String.format("Failed to load configuration anchor from %s", configurationAnchorProvider.source());

            log.error(message, e);

            throw XrdRuntimeException.systemException(MALFORMED_ANCHOR)
                    .details(message)
                    .build();
        }

        saveInstanceIdentifier();
    }

    void saveInstanceIdentifier() {
        try {
            ConfigurationDirectory.saveInstanceIdentifier(globalConfigurationDir,
                    configurationAnchor.getInstanceIdentifier());
        } catch (Exception e) {
            throw XrdRuntimeException.systemException(ErrorCode.FAILED_TO_SAVE_INSTANCE_IDENTIFIER)
                    .details("Failed to save instance identifier to a file")
                    .cause(e)
                    .build();
        }
    }

    private DownloadResult downloadConfigurationFromAnchor() throws Exception {
        log.debug("downloadConfFromAnchor()");

        DownloadResult downloadResult = downloader.download(configurationAnchor);
        handleResult(downloadResult, true);
        return downloadResult;
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private PrivateParameters loadPrivateParameters() {
        try {
            Path privateParamsPath = Path.of(globalConfigurationDir, configurationAnchor.getInstanceIdentifier(),
                    ConfigurationConstants.FILE_NAME_PRIVATE_PARAMETERS);

            if (!Files.exists(privateParamsPath)) {
                log.debug("Skipping reading private parameters as {} does not exist", privateParamsPath);
                return null;
            }
            return ParametersProviderFactory.forGlobalConfVersion(getVersion(privateParamsPath))
                    .privateParametersProvider(privateParamsPath, OffsetDateTime.MAX)
                    .getPrivateParameters();
        } catch (Exception e) {
            log.error("Failed to read additional configuration sources from {}", globalConfigurationDir, e);
            return null;
        }
    }

    protected void deleteExtraConfigurationDirectories(List<? extends ConfigurationSource> configurationSources,
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

    private void downloadConfigurationFromAdditionalSources(List<? extends ConfigurationSource> configurationSources,
                                                            FederationConfigurationSourceFilter sourceFilter) throws Exception {
        if (configurationSources != null) {
            for (ConfigurationSource source : configurationSources) {
                if (sourceFilter.shouldDownloadConfigurationFor(source.getInstanceIdentifier())) {
                    DownloadResult result = downloader.download(
                            source,
                            ConfigurationConstants.CONTENT_ID_SHARED_PARAMETERS);
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
