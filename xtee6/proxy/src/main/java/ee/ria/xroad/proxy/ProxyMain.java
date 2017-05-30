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
package ee.ria.xroad.proxy;

import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.pattern.Patterns;
import akka.util.Timeout;
import com.typesafe.config.ConfigFactory;
import ee.ria.xroad.common.*;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.monitoring.MonitorAgent;
import ee.ria.xroad.common.signature.BatchSigner;
import ee.ria.xroad.common.util.*;
import ee.ria.xroad.proxy.clientproxy.ClientProxy;
import ee.ria.xroad.proxy.messagelog.MessageLog;
import ee.ria.xroad.proxy.serverproxy.ServerProxy;
import ee.ria.xroad.proxy.util.CertHashBasedOcspResponder;
import ee.ria.xroad.signer.protocol.SignerClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import scala.concurrent.Await;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

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
                .load();

        org.apache.xml.security.Init.init();
    }

    private static final int CONNECTION_TIMEOUT_MS = 1200;
    private static final List<StartStop> SERVICES = new ArrayList<>();

    private static ActorSystem actorSystem;

    private static String version;

    private ProxyMain() {
    }

    /**
     * @return proxy version
     */
    public static String getVersion() {
        return version;
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

        for (StartStop service: SERVICES) {
            String name = service.getClass().getSimpleName();
            try {
                service.start();
                log.info("{} started", name);
            } catch (Exception e) {
                log.error(name + " failed to start", e);
                stopServices();
            }
        }

        for (StartStop service: SERVICES) {
            service.join();
        }
    }

    private static void stopServices() throws Exception {
        for (StartStop s: SERVICES) {
            log.debug("Stopping " + s.getClass().getSimpleName());
            s.stop();
            s.join();
        }
    }

    private static void startup() throws Exception {
        log.trace("startup()");

        actorSystem = ActorSystem.create("Proxy",
                ConfigFactory.load().getConfig("proxy"));

        readProxyVersion();

        log.info("Starting proxy ({})...", getVersion());
    }

    private static void shutdown() throws Exception {
        log.trace("shutdown()");

        stopServices();
        actorSystem.shutdown();
    }

    private static void createServices() throws Exception {
        JobManager jobManager = new JobManager();

        MonitorAgent.init(actorSystem);
        SignerClient.init(actorSystem);
        BatchSigner.init(actorSystem);
        MessageLog.init(actorSystem, jobManager);

        SERVICES.add(jobManager);
        SERVICES.add(new ClientProxy());
        SERVICES.add(new ServerProxy());

        SERVICES.add(new CertHashBasedOcspResponder());

        SERVICES.add(new SystemMonitor());

        SERVICES.add(createAdminPort());
    }

    private static void loadConfigurations() {
        log.trace("loadConfigurations()");

        try {
            GlobalConf.reload();
        } catch (Exception e) {
            log.error("Failed to load GlobalConf", e);
        }
    }

    private static AdminPort createAdminPort() throws Exception {
        AdminPort adminPort = new AdminPort(PortNumbers.ADMIN_PORT);

        adminPort.addShutdownHook(new Runnable() {
            @Override
            public void run() {
                log.info("Proxy shutting down...");
                try {
                    shutdown();
                } catch (Exception e) {
                    log.error("Error while shutdown", e);
                }
            }
        });

        /**
         * Diganostics for timestamping.
         * First check the connection to timestamp server. If OK, check the status of the previous timestamp request.
         * If the previous request has failed or connection cannot be made, DiagnosticsStatus tells the reason.
         */
        adminPort.addHandler("/timestampstatus", new AdminPort.SynchronousCallback() {
            @Override
            public void run() {
                try {
                    log.info("/timestampstatus");

                    Map<String, DiagnosticsStatus> result = checkConnectionToTimestampUrl();
                    log.info("result {}", result);

                    ActorSelection logManagerSelection = actorSystem.actorSelection("/user/LogManager");

                    Timeout timeout = new Timeout(CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    Map<String, DiagnosticsStatus> statusFromLogManager = (Map<String, DiagnosticsStatus>) Await.result(
                            Patterns.ask(
                                    logManagerSelection, CommonMessages.TIMESTAMP_STATUS, timeout),
                                    timeout.duration());

                    log.info("statusFromLogManager {}", statusFromLogManager.toString());

                    // Use the status either from simple connection check or from LogManager.
                    for (String key : result.keySet()) {
                        // If status exists in LogManager for given timestamp server, and it is successful or if
                        // simple connection check status is unsuccesful, use the status from LogManager
                        if (statusFromLogManager.get(key) != null
                                && (DiagnosticsErrorCodes.RETURN_SUCCESS == statusFromLogManager.get(key)
                                .getReturnCode() && DiagnosticsErrorCodes.RETURN_SUCCESS == result.get(key)
                                .getReturnCode()
                                || DiagnosticsErrorCodes.RETURN_SUCCESS != result.get(key).getReturnCode()
                                && DiagnosticsErrorCodes.RETURN_SUCCESS != statusFromLogManager.get(key)
                                .getReturnCode())) {
                            result.put(key, statusFromLogManager.get(key));
                            log.info("Using time stamping status from LogManager for url {} "
                                    + "status: {}", key,
                                    statusFromLogManager.get(key));
                        } else if (statusFromLogManager.get(key) == null && DiagnosticsErrorCodes.RETURN_SUCCESS
                                == result.get(key).getReturnCode()) {
                            result.get(key).setReturnCodeNow(DiagnosticsErrorCodes.ERROR_CODE_TIMESTAMP_UNINITIALIZED);
                        }
                    }


                    JsonUtils.getSerializer().toJson(result, getParams().response.getWriter());

                } catch (Exception e) {
                    log.error("Error getting timeout status {}", e);
                }
            }
        });

        return adminPort;
    }

    private static Map<String, DiagnosticsStatus> checkConnectionToTimestampUrl() {

        Map<String, DiagnosticsStatus> statuses = new HashMap<String, DiagnosticsStatus>();
        for (String tspUrl: ServerConf.getTspUrl()) {
            try {

                URL url = new URL(tspUrl);
                log.info("Checking timestamp server status for url {}", url);
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setConnectTimeout(CONNECTION_TIMEOUT_MS);
                con.setDoOutput(true);
                con.setDoInput(true);
                con.setRequestMethod("POST");
                con.setRequestProperty("Content-type", "application/timestamp-query");
                con.connect();
                log.info("Checking timestamp server con {}", con);

                if (con.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    log.warn("Timestamp check received HTTP error: {} - {}. Might still be ok", con.getResponseCode(),
                            con.getResponseMessage());
                    statuses.put(tspUrl, new DiagnosticsStatus(DiagnosticsErrorCodes.RETURN_SUCCESS, LocalTime.now(),
                            tspUrl));
                } else {
                    statuses.put(tspUrl, new DiagnosticsStatus(DiagnosticsErrorCodes.RETURN_SUCCESS, LocalTime.now(),
                            tspUrl));
                }

            } catch (Exception e) {
                log.warn("Timestamp status check failed {}", e);
                statuses.put(tspUrl, new DiagnosticsStatus(DiagnosticsUtils.getErrorCode(e), LocalTime.now(), tspUrl));
            }
        }
        return statuses;

    }

    private static void readProxyVersion() {
        try {
            String cmd;

            if (Files.exists(Paths.get("/etc/redhat-release"))) {
                cmd = "rpm -q --queryformat '%{VERSION}-%{RELEASE}' xroad-proxy";
            } else {
                cmd = "dpkg-query -f '${Version}' -W xroad-proxy";
            }

            Process p = Runtime.getRuntime().exec(cmd);
            p.waitFor();
            version = IOUtils.toString(p.getInputStream()).replace("'", "");

            if (StringUtils.isBlank(version)) {
                version = "unknown";

                log.warn("Unable to read proxy version: {}",
                        IOUtils.toString(p.getErrorStream()));
            }
        } catch (Exception ex) {
            version = "unknown";
            log.warn("Unable to read proxy version", ex);
        }
    }
}
