package ee.cyber.sdsb.proxy;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import akka.actor.ActorSystem;

import com.typesafe.config.ConfigFactory;

import ee.cyber.sdsb.common.PortNumbers;
import ee.cyber.sdsb.common.conf.GlobalConf;
import ee.cyber.sdsb.common.monitoring.MonitorAgent;
import ee.cyber.sdsb.common.signature.BatchSigner;
import ee.cyber.sdsb.common.util.AdminPort;
import ee.cyber.sdsb.common.util.JobManager;
import ee.cyber.sdsb.common.util.StartStop;
import ee.cyber.sdsb.common.util.SystemMonitor;
import ee.cyber.sdsb.proxy.clientproxy.ClientProxy;
import ee.cyber.sdsb.proxy.securelog.MessageLog;
import ee.cyber.sdsb.proxy.serverproxy.ServerProxy;
import ee.cyber.sdsb.proxy.util.CertHashBasedOcspResponder;
import ee.cyber.sdsb.signer.protocol.SignerClient;

/**
 * Main program for the proxy server.
 */
@Slf4j
public class ProxyMain {

    static {
        org.apache.xml.security.Init.init();
    }

    private static final List<StartStop> services = new ArrayList<>();

    private static ActorSystem actorSystem;

    private static String version;

    public static String getVersion() {
        return version;
    }

    public static void main(String args[]) throws Exception {
        startup();
        loadConfigurations();
        startServices();
        shutdown();
    }

    private static void startServices() throws Exception {
        log.trace("startServices()");

        createServices();

        for (StartStop service: services) {
            String name = service.getClass().getSimpleName();
            try {
                service.start();
                log.info("{} started", name);
            } catch (Exception e) {
                log.error(name + " failed to start", e);

                stopServices();
                System.exit(1);
            }
        }

        for (StartStop service: services) {
            service.join();
        }
    }

    private static void stopServices() throws Exception {
        for (StartStop s: services) {
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

        services.add(jobManager);
        services.add(new ClientProxy());
        services.add(new ServerProxy());

        services.add(new CertHashBasedOcspResponder());

        services.add(new SystemMonitor());

        services.add(createAdminPort());
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

        return adminPort;
    }

    private static void readProxyVersion() {
        try {
            Process p = Runtime.getRuntime().exec(
                    "dpkg-query -f '${Version}' -W xroad-proxy");
            p.waitFor();
            version = IOUtils.toString(p.getInputStream()).replace("'", "");

            if (StringUtils.isBlank(version)) {
                version = "unknown";

                log.warn("Unable to read proxy version: {}",
                        IOUtils.toString(p.getErrorStream()));
            }
        } catch (Throwable t) {
            version = "unknown";

            log.warn("Unable to read proxy version", t);
        }
    }
}
