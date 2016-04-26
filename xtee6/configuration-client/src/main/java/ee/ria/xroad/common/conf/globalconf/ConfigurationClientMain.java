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

import ee.ria.xroad.common.DiagnosticsErrorCodes;
import ee.ria.xroad.common.DiagnosticsStatus;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.SystemPropertiesLoader;
import ee.ria.xroad.common.util.AdminPort;
import ee.ria.xroad.common.util.JobManager;
import ee.ria.xroad.common.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static ee.ria.xroad.common.DiagnosticsErrorCodes.ERROR_CODE_MISSING_PRIVATE_PARAMS;
import static ee.ria.xroad.common.DiagnosticsErrorCodes.RETURN_SUCCESS;
import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.SystemProperties.CONF_FILE_PROXY;
import static ee.ria.xroad.common.conf.globalconf.PrivateParameters.CONTENT_ID_PRIVATE_PARAMETERS;

/**
 * Main program of configuration client.
 */
@Slf4j
public final class ConfigurationClientMain {

    private static ConfigurationClientJobListener listener;

    static {
        SystemPropertiesLoader.create().withCommonAndLocal()
            .with(CONF_FILE_PROXY, "configuration-client")
            .load();
        listener = new ConfigurationClientJobListener();
    }

    private static final String OPTION_VERIFY_PRIVATE_PARAMS_EXISTS =
            "verifyPrivateParamsExists";

    private static ConfigurationClient client;
    private static JobManager jobManager;
    private static AdminPort adminPort;

    private ConfigurationClientMain() {
    }

    /**
     * Main entry point of configuration client. Based on the arguments,
     * the client will either:
     * 1) <anchor file> <configuration path> -- download and exit
     * 2) <anchor file> -- download and verify
     * 3) [no args] -- start as daemon
     * @param args the arguments
     * @throws Exception if an error occurs
     */
    public static void main(String[] args) throws Exception {
        CommandLine cmd = getCommandLine(args);

        String[] actualArgs = cmd.getArgs();
        if (actualArgs.length == 2) {
            System.exit(download(actualArgs[0], actualArgs[1]));
        } else if (actualArgs.length == 1) {
            System.exit(validate(actualArgs[0],
                    cmd.hasOption(OPTION_VERIFY_PRIVATE_PARAMS_EXISTS)));
        } else {
            startDaemon();
        }
    }

    private static CommandLine getCommandLine(String[] args) throws Exception {
        CommandLineParser parser = new BasicParser();
        Options options = new Options();

        options.addOption(OPTION_VERIFY_PRIVATE_PARAMS_EXISTS, false,
                "Verifies that configuration contains private parameters.");

        return parser.parse(options, args);
    }

    private static int download(String configurationAnchorFile,
            String configurationPath) throws Exception {
        log.trace("Downloading configuration using anchor {} (path = {})",
                configurationAnchorFile, configurationPath);

        System.setProperty(SystemProperties.CONFIGURATION_ANCHOR_FILE,
                configurationAnchorFile);
        System.setProperty(SystemProperties.CONFIGURATION_PATH,
                configurationPath);

        FileNameProvider fileNameProvider =
                new FileNameProviderImpl(configurationPath);

        client = new ConfigurationClient(getDummyDownloadedFiles(),
                        new ConfigurationDownloader(fileNameProvider) {
                    @Override
                    void addAdditionalConfigurationSources(
                            PrivateParameters privateParameters) {
                        // do not download additional sources
                    }
                }) {
            @Override
            void initAdditionalConfigurationSources() {
                // not needed
            }
        };

        return execute();
    }

    private static int validate(String configurationAnchorFile,
            boolean verifyPrivate) throws Exception {
        log.trace("Downloading configuration using anchor {}",
                configurationAnchorFile);

        System.setProperty(SystemProperties.CONFIGURATION_ANCHOR_FILE,
                configurationAnchorFile);

        final AtomicBoolean foundPrivateParams = new AtomicBoolean();

        // create configuration that does not persist files to disk
        ConfigurationDownloader configuration =
                new ConfigurationDownloader(getDefaultFileNameProvider()) {
            @Override
            void handle(ConfigurationLocation location,
                    ConfigurationFile file) {
                if (CONTENT_ID_PRIVATE_PARAMETERS.equals(
                        file.getContentIdentifier())) {
                    foundPrivateParams.set(true);
                }

                super.handle(location, file);
            }

            @Override
            void persistContent(byte[] content, Path destination,
                    ConfigurationFile file) throws Exception {
            }

            @Override
            void updateExpirationDate(Path destination, ConfigurationFile file)
                    throws Exception {
            }
        };

        client = new ConfigurationClient(getDummyDownloadedFiles(),
                        configuration) {
            @Override
            void initAdditionalConfigurationSources() {
                // not needed
            }

            @Override
            void saveInstanceIdentifier() {
                // not needed
            }
        };

        int result = execute();

        // Check if downloaded configuration contained private parameters
        if (result == RETURN_SUCCESS && verifyPrivate
                && !foundPrivateParams.get()) {
            return ERROR_CODE_MISSING_PRIVATE_PARAMS;
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

    private static ConfigurationClient createClient() {
        ConfigurationDownloader configuration =
                new ConfigurationDownloader(getDefaultFileNameProvider());

        Path downloadedFilesConf =
                Paths.get(SystemProperties.getConfigurationPath(), "files");

        return new ConfigurationClient(
                new DownloadedFiles(downloadedFilesConf), configuration);
    }

    private static FileNameProviderImpl getDefaultFileNameProvider() {
        return new FileNameProviderImpl(
                SystemProperties.getConfigurationPath());
    }

    private static DownloadedFiles getDummyDownloadedFiles() {
        return new DownloadedFiles(null) {
            @Override
            void delete(String file) {
            }

            @Override
            void load() throws Exception {
            }

            @Override
            void save() throws Exception {
            }
        };
    }

    private static void setup() throws Exception {
        log.trace("setUp()");

        client = createClient();

        adminPort = new AdminPort(SystemProperties.getConfigurationClientAdminPort());

        adminPort.addShutdownHook(new Runnable() {
            @Override
            public void run() {
                log.info("Configuration client shutting down...");
                try {
                    shutdown();
                } catch (Exception e) {
                    log.error("Error while shutting down", e);
                }
            }
        });

        adminPort.addHandler("/execute", new AdminPort.SynchronousCallback() {
            @Override
            public void run() {
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
            public void run() {
                try {
                    log.info("handler /status");
                    JsonUtils.getSerializer().toJson(listener.getStatus(), getParams().response.getWriter());
                } catch (Exception e) {
                    log.error("Error getting conf client status {}", e);
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
                SystemProperties.getConfigurationClientUpdateIntervalSeconds(),
                data);

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
     * Listens for daemon job completions and collects results
     */
    @Slf4j
    private static class ConfigurationClientJobListener implements JobListener {

        public static final String LISTENER_NAME = "confClientJobListener";

        // access only via synchronized getter/setter
        private static DiagnosticsStatus status;

        static {
            status = new DiagnosticsStatus(DiagnosticsErrorCodes.ERROR_CODE_UNINITIALIZED, LocalTime.now(),
                    LocalTime.now().plusSeconds(SystemProperties.getConfigurationClientUpdateIntervalSeconds()));
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
            log.info("job was executed result={}", context.getResult());
            setStatus((DiagnosticsStatus) context.getResult());
        }
    }
}
