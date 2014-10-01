package ee.cyber.sdsb.distributedfiles;

import lombok.extern.slf4j.Slf4j;
import akka.actor.ActorSystem;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import ee.cyber.sdsb.common.ErrorCodes;
import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.util.AdminPort;
import ee.cyber.sdsb.common.util.JobManager;

@Slf4j
public class DistributedFilesClientMain {

    private static ActorSystem actorSystem;
    private static JobManager jobManager;
    private static AdminPort adminPort;

    public static void main(String[] args) throws Exception {
        setup();
        startServices();
        awaitTermination();
        shutdown();
    }

    private static void setup() throws Exception {
        log.trace("setUp()");

        int portNumber = SystemProperties.getDistributedFilesClientPort();
        adminPort = new AdminPort(portNumber + 1);

        adminPort.addShutdownHook(new Runnable() {
            @Override
            public void run() {
                log.info("Distributed files client shutting down...");
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
                log.info("Execute from admin port...");
                try {
                    DistributedFilesClient.execute();
                } catch (Exception e) {
                    throw ErrorCodes.translateException(e);
                }
            }
        });

        actorSystem = ActorSystem.create("DistributedFilesClient",
                getConf(portNumber));
    }

    private static void startServices() throws Exception {
        log.trace("startServices()");

        adminPort.start();

        jobManager = new JobManager();
        jobManager.registerRepeatingJob(DistributedFilesClient.class, 60);

        jobManager.start();
    }

    private static void awaitTermination() {
        log.info("DistributedFilesClient started");

        actorSystem.awaitTermination();
    }

    private static void shutdown() throws Exception {
        log.trace("tearDown()");

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
