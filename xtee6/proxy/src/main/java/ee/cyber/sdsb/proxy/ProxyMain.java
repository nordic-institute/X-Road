package ee.cyber.sdsb.proxy;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorSystem;

import com.typesafe.config.ConfigFactory;

import ee.cyber.sdsb.common.PortNumbers;
import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.conf.GlobalConf;
import ee.cyber.sdsb.common.monitoring.MonitorAgent;
import ee.cyber.sdsb.common.signature.BatchSigningWorker;
import ee.cyber.sdsb.common.util.AdminPort;
import ee.cyber.sdsb.common.util.JobManager;
import ee.cyber.sdsb.common.util.StartStop;
import ee.cyber.sdsb.common.util.SystemMonitor;
import ee.cyber.sdsb.proxy.clientproxy.ClientProxy;
import ee.cyber.sdsb.proxy.conf.ServerConf;
import ee.cyber.sdsb.proxy.securelog.LogManager;
import ee.cyber.sdsb.proxy.serverproxy.ServerProxy;
import ee.cyber.sdsb.proxy.util.CertHashBasedOcspResponder;
import ee.cyber.sdsb.proxy.util.OcspClient;
import ee.cyber.sdsb.signer.protocol.SignerClient;

/**
 * Main program for the proxy server.
 */
public class ProxyMain {

    private static final Logger LOG = LoggerFactory.getLogger(ProxyMain.class);

    private static final List<StartStop> services = new ArrayList<>();

    private static ActorSystem actorSystem;

    public static void main(String args[]) throws Exception {
        setUp();
        loadConfigurations();
        startServices();
        tearDown();
    }

    private static void startServices() throws Exception {
        LOG.trace("startServices()");

        createServices();

        for (StartStop service: services) {
            String name = service.getClass().getSimpleName();
            try {
                service.start();
                LOG.info("{} started", name);
            } catch (Exception e) {
                LOG.error(name + " failed to start", e);

                shutdown();
                System.exit(1);
            }
        }

        for (StartStop service: services) {
            service.join();
        }
    }

    private static void setUp() throws Exception {
        LOG.trace("setUp()");

        actorSystem = ActorSystem.create("Proxy",
                ConfigFactory.load().getConfig("proxy"));
    }

    private static void tearDown() throws Exception {
        LOG.trace("tearDown()");

        actorSystem.shutdown();
    }

    private static void createServices() throws Exception {
        MonitorAgent.init(actorSystem);
        SignerClient.init(actorSystem);
        BatchSigningWorker.init(actorSystem);

        services.add(LogManager.getInstance());

        services.add(new ClientProxy());
        services.add(new ServerProxy());

        services.add(new CertHashBasedOcspResponder());
        services.add(createJobManager());

        services.add(new SystemMonitor());

        services.add(createAdminPort());
    }

    private static void loadConfigurations() {
        LOG.trace("loadConfigurations()");

        try {
            ServerConf.reload();
        } catch (Exception e) {
            LOG.error("Failed to load ServerConf", e);
        }

        try {
            GlobalConf.reload();
        } catch (Exception e) {
            LOG.error("Failed to load GlobalConf", e);
        }
    }

    private static JobManager createJobManager() throws Exception {
        JobManager jobManager = new JobManager();

        jobManager.registerRepeatingJob(OcspClient.class, 60);

        return jobManager;
    }

    private static AdminPort createAdminPort() throws Exception {
        int proxyPort = SystemProperties.getServerProxyPort();
        //AdminPort adminPort = new AdminPort(proxyPort + 1);
        AdminPort adminPort = new AdminPort(PortNumbers.ADMIN_PORT);

        adminPort.addStopHandler(new AdminPort.AsynchronousCallback() {
            @Override
            public void run() {
                LOG.info("Shutting down...");
                try {
                    shutdown();
                } catch (Exception e) {
                    LOG.error("Error while shutdown", e);
                }
            }
        });

        return adminPort;
    }

    private static void shutdown() throws Exception {
        for (StartStop s: services) {
            LOG.debug("Stopping " + s.getClass().getSimpleName());
            s.stop();
            s.join();
        }
    }
}
