/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;

import static ee.ria.xroad.common.ErrorCodes.X_INVALID_XML;

/**
 * Configuration client downloads the configuration from sources found in the
 * configuration anchor.
 */
@Slf4j
@RequiredArgsConstructor
@AllArgsConstructor
class ConfigurationClient {

    private final Map<String, Set<ConfigurationSource>> additionalSources =
            new HashMap<>();

    private final DownloadedFiles downloadedFiles;
    private final ConfigurationDownloader downloader;

    private ConfigurationAnchor configurationAnchor;

    synchronized void execute() throws Exception {
        log.trace("Configuration client executing...");

        if (configurationAnchor == null || configurationAnchor.hasChanged()) {
            initConfigurationAnchor();
        }

        downloadConfigurationFromAnchor();

        if (!additionalSources.isEmpty()) {
            downloadConfigurationFromAdditionalSources();
        }

        // only sync if download was successful
        try {
            downloadedFiles.sync();
        } catch (Exception e) {
            log.error("Failed to sync downloaded files list", e);
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
            String message = String.format(
                    "Failed to load configuration anchor from file %s",
                    anchorFileName);

            log.error(message, e);
            throw new CodedException(X_INVALID_XML, message);
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

        handleResult(downloader.download(configurationAnchor), true);

        downloader.getAdditionalSources()
            .forEach(this::putAdditionalConfigurationSources);
    }

    private void downloadConfigurationFromAdditionalSources() throws Exception {
        log.trace("Downloading configuration from additional sources ({})",
                additionalSources.size());

        for (Set<ConfigurationSource> sources : additionalSources.values()) {
            for (ConfigurationSource source : sources) {
                DownloadResult result =
                        downloader.download(source,
                                SharedParameters.CONTENT_ID_SHARED_PARAMETERS);
                handleResult(result, source.getInstanceIdentifier().equals(
                        configurationAnchor.getInstanceIdentifier()));
            }
        }
    }

    private void handleResult(DownloadResult result, boolean throwIfFailure)
            throws Exception {
        if (result.isSuccess()) {
            handleSuccess(result);
        } else {
            try {
                handleFailure(result);
            } catch (Exception e) {
                if (throwIfFailure) {
                    throw e; // re-throw
                }
            }
        }
    }

    private void handleSuccess(DownloadResult result) {
        log.trace("handleSuccess()");

        Configuration configuration = result.getConfiguration();

        downloadedFiles.add(configuration.getFiles().stream()
                .map(downloader::getFileName)
                .map(Object::toString)
                .collect(Collectors.toSet()));
    }

    private void handleFailure(DownloadResult result) throws Exception {
        final StringBuilder errorMessage =
                new StringBuilder("Failed to download configuration from any "
                        + "configuration location:\n");
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

    private void putAdditionalConfigurationSources(String instanceIdentifier,
            Collection<ConfigurationSource> sources) {
        additionalSources.put(instanceIdentifier, new HashSet<>(sources));
    }
}
