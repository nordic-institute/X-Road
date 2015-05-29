package ee.ria.xroad.proxy;

import java.util.ArrayList;
import java.util.List;

import akka.actor.ActorSystem;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import ee.ria.xroad.common.PortNumbers;
import ee.ria.xroad.common.SystemPropertiesLoader;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.monitoring.MonitorAgent;
import ee.ria.xroad.common.signature.BatchSigner;
import ee.ria.xroad.common.util.AdminPort;
import ee.ria.xroad.common.util.JobManager;
import ee.ria.xroad.common.util.StartStop;
import ee.ria.xroad.common.util.SystemMonitor;
import ee.ria.xroad.proxy.clientproxy.ClientProxy;
import ee.ria.xroad.proxy.messagelog.MessageLog;
import ee.ria.xroad.proxy.serverproxy.ServerProxy;
import ee.ria.xroad.proxy.util.CertHashBasedOcspResponder;
import ee.ria.xroad.signer.protocol.SignerClient;

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
        } catch (Throwable t) {
            log.error("Proxy failed to start", t);
            throw t;
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
                System.exit(1);
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
