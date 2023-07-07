/**
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

import ee.ria.xroad.common.DiagnosticsErrorCodes;
import ee.ria.xroad.common.DiagnosticsStatus;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.SystemPropertiesLoader;
import ee.ria.xroad.common.Version;
import ee.ria.xroad.common.util.AdminPort;
import ee.ria.xroad.common.util.JobManager;
import ee.ria.xroad.common.util.JsonUtils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.schedule.backup.ProxyConfigurationBackupJob;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static ee.ria.xroad.common.DiagnosticsErrorCodes.ERROR_CODE_ANCHOR_NOT_FOR_EXTERNAL_SOURCE;
import static ee.ria.xroad.common.DiagnosticsErrorCodes.ERROR_CODE_MISSING_PRIVATE_PARAMS;
import static ee.ria.xroad.common.DiagnosticsErrorCodes.RETURN_SUCCESS;
import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.SystemProperties.CONF_FILE_PROXY;
import static ee.ria.xroad.common.conf.globalconf.ConfigurationConstants.CONTENT_ID_PRIVATE_PARAMETERS;
import static ee.ria.xroad.common.conf.globalconf.ConfigurationConstants.CONTENT_ID_SHARED_PARAMETERS;

/**
 * Main program of configuration client.
 */
@Slf4j
public final class ConfigurationClientMain {

    private static final String APP_NAME = "xroad-confclient";

    private static ConfigurationClientJobListener listener;

    private static final int NUM_ARGS_FROM_CONF_PROXY_FULL = 3;
    private static final int NUM_ARGS_FROM_CONF_PROXY = 2;

    static {
        SystemPropertiesLoader.create()
                .withCommonAndLocal()
                .with(CONF_FILE_PROXY, "configuration-client")
                .load();

        listener = new ConfigurationClientJobListener();
    }

    private static final String OPTION_VERIFY_PRIVATE_PARAMS_EXISTS = "verifyPrivateParamsExists";
    private static final String OPTION_VERIFY_ANCHOR_FOR_EXTERNAL_SOURCE = "verifyAnchorForExternalSource";

    private static ConfigurationClient client;
    private static JobManager jobManager;
    private static AdminPort adminPort;

    private ConfigurationClientMain() {
    }

    /**
     * Main entry point of configuration client. Based on the arguments, the client will either:
     * 1) <anchor file> <configuration path> -- download and exit,
     * 2) <anchor file> -- download and verify,
     * 3) [no args] -- start as daemon.
     *
     * @param args the arguments
     * @throws Exception if an error occurs
     */
    public static void main(String[] args) throws Exception {
        Version.outputVersionInfo(APP_NAME);
        CommandLine cmd = getCommandLine(args);
        String[] actualArgs = cmd.getArgs();
        if (actualArgs.length == NUM_ARGS_FROM_CONF_PROXY_FULL) {
            // Run configuration client in one-shot mode downloading the specified global configuration version.
            System.exit(download(actualArgs[0], actualArgs[1]));
        } else if (actualArgs.length == NUM_ARGS_FROM_CONF_PROXY) {
            // Run configuration client in one-shot mode downloading the current global configuration version.
            System.exit(download(actualArgs[0], actualArgs[1]));
        } else if (actualArgs.length == 1) {
            // Run configuration client in validate mode.
            System.exit(validate(actualArgs[0], getParamsValidator(cmd)));
        } else {
            // Run configuration client in daemon mode.
            startDaemon();
        }
    }

    private static CommandLine getCommandLine(String[] args) throws Exception {
        CommandLineParser parser = new BasicParser();
        Options options = new Options();

        options.addOption(OPTION_VERIFY_PRIVATE_PARAMS_EXISTS, false,
                "Verifies that configuration contains private parameters.");
        options.addOption(OPTION_VERIFY_ANCHOR_FOR_EXTERNAL_SOURCE, false,
                "Verifies that configuration contains shared parameters.");

        return parser.parse(options, args);
    }

    private static int download(String configurationAnchorFile, String configurationPath) {
        log.debug("Downloading configuration using anchor {} path = {})",
                configurationAnchorFile, configurationPath);

        System.setProperty(SystemProperties.CONFIGURATION_ANCHOR_FILE, configurationAnchorFile);

        client = new ConfigurationClient(configurationPath) {
            @Override
            protected void deleteExtraConfigurationDirectories(
                    PrivateParametersV2 privateParameters,
                    FederationConfigurationSourceFilter sourceFilter) {
                // do not delete anything
            }
        };

        return execute();
    }

    private static int validate(String configurationAnchorFile, final ParamsValidator paramsValidator) {
        log.trace("Downloading configuration using anchor {}", configurationAnchorFile);

        // Create configuration that does not persist files to disk.
        final String configurationPath = SystemProperties.getConfigurationPath();

        ConfigurationDownloader configurationDownloader = new ConfigurationDownloader(configurationPath) {
            @Override
            void handleContent(byte[] content, ConfigurationFile file) throws Exception {
                validateContent(file);
                super.handleContent(content, file);
            }

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

        ConfigurationAnchorV2 configurationAnchor = new ConfigurationAnchorV2(configurationAnchorFile);
        client = new ConfigurationClient(configurationPath, configurationDownloader, configurationAnchor) {
            @Override
            protected void deleteExtraConfigurationDirectories(PrivateParametersV2 privateParameters,
                                                               FederationConfigurationSourceFilter sourceFilter) {
                // do not delete any files
            }

            @Override
            void saveInstanceIdentifier() {
                // Not needed.
            }
        };

        int result = execute();

        // Check if downloaded configuration contained private parameters.
        if (result == RETURN_SUCCESS) {
            return paramsValidator.getExitCode();
        }

        return result;
    }

    private static int execute() {
        try {
            client.execute();

            return RETURN_SUCCESS;
        } catch (Exception e) {
            log.error("Error when downloading conf", e);

            return ConfigurationClientUtils.getErrorCode(e);
        }
    }

    private static void startDaemon() throws Exception {
        setup();
        startServices();
        awaitTermination();
        shutdown();
    }

    private static void setup() {
        log.trace("setUp()");

        client = new ConfigurationClient(SystemProperties.getConfigurationPath());

        adminPort = new AdminPort(SystemProperties.getConfigurationClientAdminPort());

        adminPort.addShutdownHook(() -> {
            log.info("Configuration client shutting down...");

            try {
                shutdown();
            } catch (Exception e) {
                log.error("Error while shutting down", e);
            }
        });

        adminPort.addHandler("/execute", new AdminPort.SynchronousCallback() {
            @Override
            public void handle(HttpServletRequest request, HttpServletResponse response) {
                log.info("handler /execute");

                try {
                    client.execute();
                } catch (Exception e) {
                    throw translateException(e);
                }
            }
        });

        adminPort.addHandler("/status", new AdminPort.SynchronousCallback() {
            @Override
            public void handle(HttpServletRequest request, HttpServletResponse response) {
                try {
                    log.info("handler /status");

                    response.setCharacterEncoding("UTF8");
                    JsonUtils.getObjectWriter()
                            .writeValue(response.getWriter(), ConfigurationClientJobListener.getStatus());
                } catch (Exception e) {
                    log.error("Error getting conf client status", e);
                }
            }
        });
    }

    private static void startServices() throws Exception {
        log.trace("startServices()");

        adminPort.start();

        jobManager = new JobManager();
        jobManager.getJobScheduler().getListenerManager().addJobListener(listener);

        JobDataMap data = new JobDataMap();
        data.put("client", client);

        jobManager.registerRepeatingJob(ConfigurationClientJob.class,
                SystemProperties.getConfigurationClientUpdateIntervalSeconds(), data);

        jobManager.registerJob(ProxyConfigurationBackupJob.class,
                SystemProperties.getConfigurationClientProxyConfigurationBackupCron(), new JobDataMap());

        jobManager.start();
    }

    private static void awaitTermination() {
        log.info("Configuration client started");

        try {
            new CountDownLatch(1).await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void shutdown() throws Exception {
        log.trace("tearDown()");

        if (jobManager != null) {
            jobManager.stop();
        }

        if (adminPort != null) {
            adminPort.stop();
            adminPort.join();
        }
    }

    /**
     * Listens for daemon job completions and collects results.
     */
    @Slf4j
    private static class ConfigurationClientJobListener implements JobListener {
        public static final String LISTENER_NAME = "confClientJobListener";

        // Access only via synchronized getter/setter.
        private static DiagnosticsStatus status;

        static {
            status = new DiagnosticsStatus(DiagnosticsErrorCodes.ERROR_CODE_UNINITIALIZED, OffsetDateTime.now(),
                    OffsetDateTime.now().plusSeconds(SystemProperties.getConfigurationClientUpdateIntervalSeconds()));
        }

        private static synchronized void setStatus(DiagnosticsStatus newStatus) {
            status = newStatus;
        }

        private static synchronized DiagnosticsStatus getStatus() {
            return status;
        }

        @Override
        public String getName() {
            return LISTENER_NAME;
        }

        @Override
        public void jobToBeExecuted(JobExecutionContext context) {
            // NOP
        }

        @Override
        public void jobExecutionVetoed(JobExecutionContext context) {
            // NOP
        }

        @Override
        public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
            if (context.getResult() instanceof DiagnosticsStatus) {
                log.info("job was executed result={}", context.getResult());

                setStatus((DiagnosticsStatus) context.getResult());
            }
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
