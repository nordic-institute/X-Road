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
package ee.ria.xroad.proxy;

import ee.ria.xroad.common.CommonMessages;
import ee.ria.xroad.common.DiagnosticsErrorCodes;
import ee.ria.xroad.common.DiagnosticsStatus;
import ee.ria.xroad.common.DiagnosticsUtils;
import ee.ria.xroad.common.PortNumbers;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.SystemPropertiesLoader;
import ee.ria.xroad.common.Version;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.serverconf.CachingServerConfImpl;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.monitoring.MonitorAgent;
import ee.ria.xroad.common.signature.BatchSigner;
import ee.ria.xroad.common.util.AdminPort;
import ee.ria.xroad.common.util.JobManager;
import ee.ria.xroad.common.util.JsonUtils;
import ee.ria.xroad.common.util.StartStop;
import ee.ria.xroad.common.util.healthcheck.HealthCheckPort;
import ee.ria.xroad.proxy.addon.AddOn;
import ee.ria.xroad.proxy.clientproxy.ClientProxy;
import ee.ria.xroad.proxy.messagelog.MessageLog;
import ee.ria.xroad.proxy.opmonitoring.OpMonitoring;
import ee.ria.xroad.proxy.serverproxy.ServerProxy;
import ee.ria.xroad.proxy.util.CertHashBasedOcspResponder;
import ee.ria.xroad.proxy.util.GlobalConfUpdater;
import ee.ria.xroad.proxy.util.ServerConfStatsLogger;
import ee.ria.xroad.signer.protocol.SignerClient;

import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.pattern.Patterns;
import akka.util.Timeout;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import lombok.extern.slf4j.Slf4j;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;

import static ee.ria.xroad.common.SystemProperties.CONF_FILE_NODE;
import static ee.ria.xroad.common.SystemProperties.CONF_FILE_PROXY;
import static ee.ria.xroad.common.SystemProperties.CONF_FILE_SIGNER;

/**
 * Main program for the proxy server.
 */
@Slf4j
public final class ProxyMain {

    static {
        SystemPropertiesLoader.create()
                .withCommonAndLocal()
                .withAddOn()
                .with(CONF_FILE_PROXY)
                .with(CONF_FILE_SIGNER)
                .withLocalOptional(CONF_FILE_NODE)
                .load();

        org.apache.xml.security.Init.init();
    }

    private static final int DIAGNOSTICS_CONNECTION_TIMEOUT_MS = 1200;
    private static final int DIAGNOSTICS_READ_TIMEOUT_MS = 15000; // 15 seconds

    private static final List<StartStop> SERVICES = new ArrayList<>();

    private static ActorSystem actorSystem;

    private static ServiceLoader<AddOn> addOns = ServiceLoader.load(AddOn.class);

    private static final int GLOBAL_CONF_UPDATE_REPEAT_INTERVAL = 60;

    private static final int STATS_LOG_REPEAT_INTERVAL = 60;

    private ProxyMain() {
    }

    /**
     * Main program entry point.
     * @param args command-line arguments
     * @throws Exception in case of any errors
     */
    public static void main(String args[]) throws Exception {
        try {
            startup();
            loadConfigurations();
            startServices();

            log.info("hello world");
            log.info("hello world");
            log.info("hello " + '\u0085' + " world");
            log.info("hello world");
            log.info("hello world");

        } catch (Exception ex) {
            log.error("Proxy failed to start", ex);
            throw ex;
        } finally {
            shutdown();
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
            } catch (Exception e) {
                log.error(name + " failed to start", e);
                stopServices();
            }
        }

        for (StartStop service : SERVICES) {
            service.join();
        }

    }

    private static void stopServices() throws Exception {
        for (StartStop s : SERVICES) {
            log.debug("Stopping " + s.getClass().getSimpleName());
            s.stop();
            s.join();
        }
    }

    private static void startup() throws Exception {
        log.trace("startup()");
        actorSystem = ActorSystem.create("Proxy", ConfigFactory.load().getConfig("proxy")
                .withFallback(ConfigFactory.load())
                .withValue("akka.remote.artery.canonical.port",
                        ConfigValueFactory.fromAnyRef(PortNumbers.PROXY_ACTORSYSTEM_PORT)));
        log.info("Starting proxy ({})...", readProxyVersion());
    }

    private static void shutdown() throws Exception {
        log.trace("shutdown()");
        stopServices();
        Await.ready(actorSystem.terminate(), Duration.Inf());
    }

    private static void createServices() throws Exception {
        JobManager jobManager = new JobManager();

        MonitorAgent.init(actorSystem);
        SignerClient.init(actorSystem);
        BatchSigner.init(actorSystem);
        MessageLog.init(actorSystem, jobManager);
        OpMonitoring.init(actorSystem);

        for (AddOn addOn : addOns) {
            addOn.init(actorSystem);
        }

        SERVICES.add(jobManager);
        SERVICES.add(new ClientProxy());
        SERVICES.add(new ServerProxy());

        SERVICES.add(new CertHashBasedOcspResponder());

        SERVICES.add(createAdminPort());

        if (SystemProperties.isHealthCheckEnabled()) {
            SERVICES.add(new HealthCheckPort());
        }

        jobManager.registerRepeatingJob(GlobalConfUpdater.class, GLOBAL_CONF_UPDATE_REPEAT_INTERVAL);
        jobManager.registerRepeatingJob(ServerConfStatsLogger.class, STATS_LOG_REPEAT_INTERVAL);
    }

    private static void loadConfigurations() {
        log.trace("loadConfigurations()");

        try {
            if (SystemProperties.getServerConfCachePeriod() > 0) {
                ServerConf.reload(new CachingServerConfImpl());
            }
            GlobalConf.reload();
        } catch (Exception e) {
            log.error("Failed to initialize configurations", e);
        }
    }

    private static AdminPort createAdminPort() throws Exception {
        AdminPort adminPort = new AdminPort(PortNumbers.ADMIN_PORT);

        addShutdownHook(adminPort);

        addTimestampStatusHandler(adminPort);

        addMaintenanceHandler(adminPort);

        return adminPort;
    }

    private static void addMaintenanceHandler(AdminPort adminPort) {
        adminPort.addHandler("/maintenance", new AdminPort.SynchronousCallback() {
            @Override
            public void handle(HttpServletRequest request, HttpServletResponse response) {

                String result = "Invalid parameter 'targetState', request ignored";
                String param = request.getParameter("targetState");

                if (param != null && (param.equalsIgnoreCase("true") || param.equalsIgnoreCase("false"))) {
                    result = setHealthCheckMaintenanceMode(Boolean.valueOf(param));
                }
                try {
                    response.setCharacterEncoding("UTF8");
                    response.getWriter().println(result);
                } catch (IOException e) {
                    log.error("Unable to write to provided response, delegated request handling failed, response may"
                            + " be malformed", e);
                }
            }
        });
    }

    /**
     * Diganostics for timestamping.
     * First check the connection to timestamp server. If OK, check the status of the previous timestamp request.
     * If the previous request has failed or connection cannot be made, DiagnosticsStatus tells the reason. If
     * LogManager is unavailable, uses the connection check to produce a more informative status.
     */
    private static void addTimestampStatusHandler(AdminPort adminPort) {
        adminPort.addHandler("/timestampstatus", new AdminPort.SynchronousCallback() {
            @Override
            public void handle(HttpServletRequest request, HttpServletResponse response) {
                log.info("/timestampstatus");

                Map<String, DiagnosticsStatus> result = checkConnectionToTimestampUrl();
                log.info("result {}", result);

                ActorSelection logManagerSelection = actorSystem.actorSelection("/user/LogManager");

                Timeout timeout = new Timeout(DIAGNOSTICS_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                try {
                    Map<String, DiagnosticsStatus> statusFromLogManager =
                            (Map<String, DiagnosticsStatus>)Await.result(
                                    Patterns.ask(logManagerSelection, CommonMessages.TIMESTAMP_STATUS, timeout),
                                    timeout.duration());

                    log.info("statusFromLogManager {}", statusFromLogManager.toString());

                    // Use the status either from simple connection check or from LogManager.
                    for (String key : result.keySet()) {
                        // If status exists in LogManager for given timestamp server, and it is successful or if
                        // simple connection check status is unsuccessful, use the status from LogManager
                        if (statusFromLogManager.get(key) != null
                                && (DiagnosticsErrorCodes.RETURN_SUCCESS == statusFromLogManager.get(key)
                                .getReturnCode() && DiagnosticsErrorCodes.RETURN_SUCCESS == result.get(key)
                                .getReturnCode()
                                || DiagnosticsErrorCodes.RETURN_SUCCESS != result.get(key).getReturnCode()
                                && DiagnosticsErrorCodes.RETURN_SUCCESS != statusFromLogManager.get(key)
                                .getReturnCode())) {
                            result.put(key, statusFromLogManager.get(key));

                            log.info("Using time stamping status from LogManager for url {} status: {}", key,
                                    statusFromLogManager.get(key));
                        } else if (statusFromLogManager.get(key) == null
                                && DiagnosticsErrorCodes.RETURN_SUCCESS == result.get(key).getReturnCode()) {
                            result.get(key).setReturnCodeNow(DiagnosticsErrorCodes.ERROR_CODE_TIMESTAMP_UNINITIALIZED);
                        }
                    }
                } catch (Exception e) {
                    log.error("Unable to connect to LogManager, immediate timestamping status unavailable", e);
                    transmuteErrorCodes(result, DiagnosticsErrorCodes.RETURN_SUCCESS,
                            DiagnosticsErrorCodes.ERROR_CODE_LOGMANAGER_UNAVAILABLE);
                }

                try {
                    response.setCharacterEncoding("UTF8");
                    JsonUtils.getSerializer().toJson(result, response.getWriter());
                } catch (IOException e) {
                    log.error("Unable to write to provided response, delegated request handling failed, response may"
                            + " be malformed", e);
                }
            }
        });
    }

    private static void addShutdownHook(AdminPort adminPort) {
        adminPort.addShutdownHook(() -> {
            log.info("Proxy shutting down...");

            try {
                shutdown();
            } catch (Exception e) {
                log.error("Error while shutdown", e);
            }
        });
    }

    private static String setHealthCheckMaintenanceMode(boolean targetState) {
        return SERVICES.stream()
                .filter(HealthCheckPort.class::isInstance)
                .map(HealthCheckPort.class::cast)
                .findFirst()
                .map(port -> port.setMaintenanceMode(targetState))
                .orElse("No HealthCheckPort found, maintenance mode not set");
    }

    private static void transmuteErrorCodes(Map<String, DiagnosticsStatus> map, int oldErrorCode, int newErrorCode) {
        map.forEach((key, value) -> {
            if (value != null && oldErrorCode == value.getReturnCode()) {
                value.setReturnCodeNow(newErrorCode);
            }
        });
    }


    private static Map<String, DiagnosticsStatus> checkConnectionToTimestampUrl() {
        Map<String, DiagnosticsStatus> statuses = new HashMap<>();

        for (String tspUrl : ServerConf.getTspUrl()) {
            try {
                URL url = new URL(tspUrl);

                log.info("Checking timestamp server status for url {}", url);

                HttpURLConnection con = (HttpURLConnection)url.openConnection();
                con.setConnectTimeout(DIAGNOSTICS_CONNECTION_TIMEOUT_MS);
                con.setReadTimeout(DIAGNOSTICS_READ_TIMEOUT_MS);
                con.setDoOutput(true);
                con.setDoInput(true);
                con.setRequestMethod("POST");
                con.setRequestProperty("Content-type", "application/timestamp-query");
                con.connect();

                log.info("Checking timestamp server con {}", con);

                if (con.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    log.warn("Timestamp check received HTTP error: {} - {}. Might still be ok", con.getResponseCode(),
                            con.getResponseMessage());
                    statuses.put(tspUrl,
                            new DiagnosticsStatus(DiagnosticsErrorCodes.RETURN_SUCCESS, OffsetDateTime.now(),
                                    tspUrl));
                } else {
                    statuses.put(tspUrl,
                            new DiagnosticsStatus(DiagnosticsErrorCodes.RETURN_SUCCESS, OffsetDateTime.now(),
                                    tspUrl));
                }

            } catch (Exception e) {
                log.warn("Timestamp status check failed {}", e);

                statuses.put(tspUrl,
                        new DiagnosticsStatus(DiagnosticsUtils.getErrorCode(e), OffsetDateTime.now(), tspUrl));
            }
        }
        return statuses;

    }

    /**
     * Return X-Road software version
     * @return version string e.g. 6.19.0
     */
    public static String readProxyVersion() {
        return Version.XROAD_VERSION;
    }
}
