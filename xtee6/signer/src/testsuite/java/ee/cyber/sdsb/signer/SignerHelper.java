package ee.cyber.sdsb.signer;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorSystem;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.util.PasswordStore;
import ee.cyber.sdsb.signer.protocol.ComponentNames;
import ee.cyber.sdsb.signer.protocol.SignerClient;
import ee.cyber.sdsb.signer.protocol.message.InitSoftwareToken;

public class SignerHelper {

    private static final Logger LOG =
            LoggerFactory.getLogger(SignerHelper.class);

    private static final String DEVICE_CONFIGURATION_FILE =
            "build/conf/devices.ini";

    private static final String KEY_CONFIGURATION_FILE =
            "build/conf/keyconf.xml";

    private static final String GLOBAL_CONFIGURATION_FILE =
            "build/conf/globalconf.xml";

    private static Signer signer;

    private static ActorSystem actorSystem;

    public static class Start {
        @Test
        public void start() throws Exception {
            TestCase.isTestSuiteRunning = true;

            startSigner();
        }
    }

    public static class Stop {
        @Test
        public void stop() throws Exception {
            stopSigner();
        }
    }

    public static void startSigner() throws Exception {
        setUpBeforeTest();

        actorSystem = ActorSystem.create(ComponentNames.SIGNER,
                getConfWithSignerPort());

        signer = new Signer(actorSystem);
        signer.start();

        Thread.sleep(1000);

        SignerClient.init(actorSystem);

        Thread.sleep(1000);

        initSoftwareToken();

        LOG.debug("Signer is started.");
    }

    public static void stopSigner() throws Exception {
        signer.stop();
        signer.join();

        actorSystem.shutdown();
    }

    private static void setUpBeforeTest() throws Exception {
        PasswordStore.clearStore();

        File confDir = new File("build/conf");
        FileUtils.deleteDirectory(confDir);
        confDir.mkdirs();
        FileUtils.copyFileToDirectory(
                new File("src/testsuite/resources/keyconf.xml"), confDir);
        FileUtils.copyFileToDirectory(
                new File("src/testsuite/resources/globalconf.xml"), confDir);
        FileUtils.copyFileToDirectory(
                new File("src/testsuite/resources/serverconf.xml"), confDir);
        FileUtils.copyFileToDirectory(
                new File("src/testsuite/resources/devices.ini"), confDir);

        System.setProperty(SystemProperties.GLOBAL_CONFIGURATION_FILE,
                GLOBAL_CONFIGURATION_FILE);
        System.setProperty(SystemProperties.KEY_CONFIGURATION_FILE,
                KEY_CONFIGURATION_FILE);
        System.setProperty(SystemProperties.DEVICE_CONFIGURATION_FILE,
                DEVICE_CONFIGURATION_FILE);
        System.setProperty(SystemProperties.SIGNER_MODULE_INSTANCE_PROVIDER,
                "ee.cyber.sdsb.signer.dummies.MockedModuleInstanceProvider");
    }

    private static void initSoftwareToken() throws Exception {
        SignerClient.execute(new InitSoftwareToken("test".toCharArray()));
    }

    private static Config getConfWithSignerPort() {
        Config conf = ConfigFactory.load().getConfig("signer-main");

        return conf.withValue("akka.remote.netty.tcp.port",
                ConfigValueFactory.fromAnyRef(
                        SystemProperties.getSignerPort()));
    }
}
