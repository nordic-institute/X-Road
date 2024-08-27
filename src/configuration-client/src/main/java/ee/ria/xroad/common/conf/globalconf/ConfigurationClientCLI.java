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

import ee.ria.xroad.common.SystemProperties;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static ee.ria.xroad.common.DiagnosticsErrorCodes.ERROR_CODE_ANCHOR_NOT_FOR_EXTERNAL_SOURCE;
import static ee.ria.xroad.common.DiagnosticsErrorCodes.ERROR_CODE_MISSING_PRIVATE_PARAMS;
import static ee.ria.xroad.common.DiagnosticsErrorCodes.RETURN_SUCCESS;
import static ee.ria.xroad.common.conf.globalconf.ConfigurationConstants.CONTENT_ID_PRIVATE_PARAMETERS;
import static ee.ria.xroad.common.conf.globalconf.ConfigurationConstants.CONTENT_ID_SHARED_PARAMETERS;

@Slf4j
@NoArgsConstructor(access = lombok.AccessLevel.NONE)
public final class ConfigurationClientCLI {
    public static final String OPTION_VERIFY_PRIVATE_PARAMS_EXISTS = "verifyPrivateParamsExists";
    public static final String OPTION_VERIFY_ANCHOR_FOR_EXTERNAL_SOURCE = "verifyAnchorForExternalSource";

    public static int download(String configurationAnchorFile, String configurationPath, int configurationVersion) {
        log.debug("Downloading configuration using anchor {} path = {} version {}",
                configurationAnchorFile,
                configurationPath,
                configurationVersion);

        System.setProperty(SystemProperties.CONFIGURATION_ANCHOR_FILE, configurationAnchorFile);

        var client = new ConfigurationClient(configurationPath, configurationVersion) {
            @Override
            protected void deleteExtraConfigurationDirectories(
                    List<? extends ConfigurationSource> configurationSources,
                    FederationConfigurationSourceFilter sourceFilter) {
                // do not delete anything
            }
        };

        return execute(client);
    }

    public static int download(String configurationAnchorFile, String configurationPath) {
        log.debug("Downloading configuration using anchor {} path = {})",
                configurationAnchorFile, configurationPath);

        System.setProperty(SystemProperties.CONFIGURATION_ANCHOR_FILE, configurationAnchorFile);

        var client = new ConfigurationClient(configurationPath) {
            @Override
            protected void deleteExtraConfigurationDirectories(
                    List<? extends ConfigurationSource> configurationSources,
                    FederationConfigurationSourceFilter sourceFilter) {
                // do not delete anything
            }
        };

        return execute(client);
    }

    public static int validate(String configurationAnchorFile, final CommandLine cmd) {
        log.trace("Downloading configuration using anchor {}", configurationAnchorFile);
        var paramsValidator = getParamsValidator(cmd);
        // Create configuration that does not persist files to disk.
        final String configurationPath = SystemProperties.getConfigurationPath();

        var configurationDownloader = new ConfigurationDownloader(configurationPath) {
            @Override
            void validateContent(ConfigurationFile file) {
                paramsValidator.tryMarkValid(file.getContentIdentifier());
            }

            @Override
            Set<Path> persistAllContent(
                    List<ConfigurationDownloader.DownloadedContent> downloadedContents) {
                // empty because we don't want to persist files to disk
                // can return empty list because extra files deletion method is also empty
                return Collections.emptySet();
            }

            @Override
            void deleteExtraFiles(String instanceIdentifier, Set<Path> neededFiles) {
                // do not delete anything
            }

        };

        ConfigurationAnchor configurationAnchor = new ConfigurationAnchor(configurationAnchorFile);
        var client = new ConfigurationClient(configurationPath, configurationDownloader, configurationAnchor) {
            @Override
            protected void deleteExtraConfigurationDirectories(List<? extends ConfigurationSource> configurationSources,
                                                               FederationConfigurationSourceFilter sourceFilter) {
                // do not delete any files
            }

            @Override
            void saveInstanceIdentifier() {
                // Not needed.
            }
        };

        int result = execute(client);
        // Check if downloaded configuration contained private parameters.
        if (result == RETURN_SUCCESS) {
            return paramsValidator.getExitCode();
        }

        return result;
    }

    private static int execute(ConfigurationClient client) {
        try {
            client.execute();

            return RETURN_SUCCESS;
        } catch (Exception e) {
            log.error("Error when downloading conf", e);

            return ConfigurationClientUtils.getErrorCode(e);
        }
    }

    private static ParamsValidator getParamsValidator(CommandLine cmd) {
        if (cmd.hasOption(OPTION_VERIFY_PRIVATE_PARAMS_EXISTS)) {
            return new ParamsValidator(CONTENT_ID_PRIVATE_PARAMETERS, ERROR_CODE_MISSING_PRIVATE_PARAMS);
        } else if (cmd.hasOption(OPTION_VERIFY_ANCHOR_FOR_EXTERNAL_SOURCE)) {
            return new SharedParamsValidator(CONTENT_ID_SHARED_PARAMETERS, ERROR_CODE_ANCHOR_NOT_FOR_EXTERNAL_SOURCE);
        } else {
            return new ParamsValidator(null, 0);
        }
    }

    private static class ParamsValidator {
        protected final AtomicBoolean valid = new AtomicBoolean();

        private final String expectedContentId;
        private final int exitCodeWhenInvalid;

        ParamsValidator(String expectedContentId, int exitCodeWhenInvalid) {
            this.expectedContentId = expectedContentId;
            this.exitCodeWhenInvalid = exitCodeWhenInvalid;
        }

        void tryMarkValid(String contentId) {
            log.trace("tryMarkValid({})", contentId);

            if (valid.get()) {
                return;
            }

            valid.set(StringUtils.isBlank(expectedContentId) || StringUtils.equals(expectedContentId, contentId));
        }

        int getExitCode() {
            if (valid.get()) {
                return RETURN_SUCCESS;
            }

            return exitCodeWhenInvalid;
        }
    }

    private static class SharedParamsValidator extends ParamsValidator {
        private final AtomicBoolean privateParametersIncluded = new AtomicBoolean();

        SharedParamsValidator(String expectedContentId, int exitCodeWhenInvalid) {
            super(expectedContentId, exitCodeWhenInvalid);
        }

        @Override
        void tryMarkValid(String contentId) {
            if (StringUtils.equals(contentId, CONTENT_ID_PRIVATE_PARAMETERS)) {
                privateParametersIncluded.set(true);
            }

            if (privateParametersIncluded.get()) {
                valid.set(false);

                return;
            }

            super.tryMarkValid(contentId);
        }
    }
}
