package ee.cyber.sdsb.distributedfiles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorSystem;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import ee.cyber.sdsb.common.ErrorCodes;
import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.util.AdminPort;
import ee.cyber.sdsb.common.util.JobManager;

public class DistributedFilesClientMain {

    private static final Logger LOG =
            LoggerFactory.getLogger(DistributedFilesClientMain.class);

    private static ActorSystem actorSystem;
    private static JobManager jobManager;
    private static AdminPort adminPort;

    public static void main(String[] args) throws Exception {
        setUp();

        startServices();

        tearDown();
    }

    private static void setUp() throws Exception {
        LOG.trace("setUp()");

        int portNumber = SystemProperties.getDistributedFilesClientPort();
        adminPort = new AdminPort(portNumber + 1);

        adminPort.addStopHandler(new AdminPort.AsynchronousCallback() {
            @Override
            public void run() {
                LOG.info("Shutting down...");
                try {
                    tearDown();
                } catch (Exception e) {
                    LOG.error("Error while shutting down", e);
                }
            }
        });

        adminPort.addHandler("/execute", new AdminPort.SynchronousCallback() {
            @Override
            public void run() {
                LOG.info("Execute from admin port...");
                try {
                    DistributedFilesClient.execute();
                } catch (Exception e) {
                    throw ErrorCodes.translateException(e);
                }
            }
        });

        actorSystem = ActorSystem.create("DistributedFilesClient",
                getConf(portNumber));

        DistributedFilesClient.init(actorSystem);
    }

    private static void startServices() throws Exception {
        LOG.trace("startServices()");

        adminPort.start();

        jobManager = new JobManager();
        jobManager.registerRepeatingJob(DistributedFilesClient.class, 60);

        jobManager.start();

        actorSystem.awaitTermination();
    }

    private static void tearDown() throws Exception {
        LOG.trace("tearDown()");

        if (jobManager != null) {
            jobManager.stop();
        }

        if (actorSystem != null) {
            actorSystem.shutdown();
        }

        if (adminPort != null) {
            adminPort.stop();
            adminPort.join();
        }
    }

    private static Config getConf(int port) {
        Config conf = ConfigFactory.load().getConfig("distributed-files");

        return conf.withValue("akka.remote.netty.tcp.port",
                ConfigValueFactory.fromAnyRef(port));
    }
}
