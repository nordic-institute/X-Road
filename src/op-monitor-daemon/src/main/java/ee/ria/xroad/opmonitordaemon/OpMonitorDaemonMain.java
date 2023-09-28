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
package ee.ria.xroad.opmonitordaemon;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.SystemPropertiesLoader;
import ee.ria.xroad.common.Version;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.globalconf.GlobalConfUpdater;
import ee.ria.xroad.common.opmonitoring.OpMonitoringSystemProperties;
import ee.ria.xroad.common.util.AdminPort;
import ee.ria.xroad.common.util.JobManager;
import ee.ria.xroad.common.util.StartStop;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

import static ee.ria.xroad.common.SystemProperties.CONF_FILE_OP_MONITOR;

/**
 * The main class of the operational monitoring daemon.
 * This class is responsible for creating the request handlers for receiving
 * and providing monitoring data.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OpMonitorDaemonMain {
    private static final String APP_NAME = "xroad-opmonitor";

    static {
        SystemPropertiesLoader.create().withCommonAndLocal()
                .with(CONF_FILE_OP_MONITOR, "op-monitor")
                .load();
    }

    private static final List<StartStop> SERVICES = new ArrayList<>();

    /**
     * Main entry point of the daemon.
     *
     * @param args command-line arguments
     * @throws Exception in case of any errors
     */
    public static void main(String[] args) throws Exception {
        try {
            startup();
            loadConfigurations();
            startServices();
        } catch (Exception e) {
            log.error("Operational monitoring daemon failed to start", e);

            throw e;
        } finally {
            shutdown();
        }
    }

    private static void startup() {
        log.info("Starting the operational monitoring daemon");
        Version.outputVersionInfo(APP_NAME);
    }

    private static void loadConfigurations() {
        log.trace("loadConfigurations()");

        try {
            GlobalConf.reload();
        } catch (Exception e) {
            log.error("Failed to load GlobalConf", e);
        }
    }

    private static void startServices() throws Exception {
        log.trace("startServices()");

        createServices();

        for (StartStop service : SERVICES) {
            String name = service.getClass().getSimpleName();

            try {
                service.start();

                log.info("{} started", name);
            } catch (Throwable e) { // We want to catch serious errors as well
                log.error(name + " failed to start", e);

                stopServices();
                System.exit(1);
            }
        }

        for (StartStop service : SERVICES) {
            service.join();
        }
    }

    private static void createServices() throws Exception {
        JobManager jobManager = new JobManager();

        OperationalDataRecordCleaner.init(jobManager);

        SERVICES.add(jobManager);
        SERVICES.add(new OpMonitorDaemon());
        SERVICES.add(createAdminPort());

        jobManager.registerRepeatingJob(GlobalConfUpdater.class,
                SystemProperties.getConfigurationClientUpdateIntervalSeconds());
    }

    private static AdminPort createAdminPort() throws Exception {
        AdminPort adminPort = new AdminPort(
                OpMonitoringSystemProperties.getOpMonitorPort() + 1);

        adminPort.addShutdownHook(() -> {
            log.info("Operational monitoring daemon shutting down...");

            try {
                shutdown();
            } catch (Exception e) {
                log.error("Error during shutdown", e);
            }
        });

        return adminPort;
    }

    private static void stopServices() throws Exception {
        for (StartStop service : SERVICES) {
            log.debug("Stopping " + service.getClass().getSimpleName());

            service.stop();
            service.join();
        }
    }

    private static void shutdown() throws Exception {
        log.info("Shutting down the operational monitoring daemon");
        stopServices();
    }
}

