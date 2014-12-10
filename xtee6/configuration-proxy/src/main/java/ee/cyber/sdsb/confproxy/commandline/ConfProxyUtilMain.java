package ee.cyber.sdsb.confproxy.commandline;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import ee.cyber.sdsb.common.SystemPropertiesLoader;
import ee.cyber.sdsb.signer.protocol.SignerClient;
import static ee.cyber.sdsb.common.SystemProperties.CONF_FILE_CONFPROXY;

/**
 * Main program for launching configuration proxy utility tools.
 */
@Slf4j
public final class ConfProxyUtilMain {

    static {
        new SystemPropertiesLoader() {
            @Override
            protected void loadWithCommonAndLocal() {
                load(CONF_FILE_CONFPROXY, "configuration-proxy");
            }
        };
    }

    private static ActorSystem actorSystem;
    private static CommandLineParser cmdLineGnuParser;

    private ConfProxyUtilMain() {
        
    }

    /**
     * Configuration proxy utility tool program entry point.
     * @param args program args
     */
    public static void main(String[] args) {
        try {
            setup();
            runUtilWithArgs(args);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            log.error("Error while running confproxy util:", e);
        } finally {
            actorSystem.shutdown();
        }
    }

    static void setup() throws Exception {
        actorSystem = ActorSystem.create("ConfigurationProxyUtil",
                ConfigFactory.load().getConfig("configuration-proxy"));

        SignerClient.init(actorSystem);

        cmdLineGnuParser = new GnuParser();
    }

    static void runUtilWithArgs(String[] args) throws Exception {
        String utilClass = args[0];
        ConfProxyUtil util = createUtilInstance(utilClass);

        Options opts = util.getOptions();
        CommandLine commandLine = cmdLineGnuParser.parse(opts, args);
        util.execute(commandLine);
    }

    @SuppressWarnings("unchecked")
    static ConfProxyUtil createUtilInstance(String className) throws Exception {
        Class<ConfProxyUtil> utilClass =
                (Class<ConfProxyUtil>) Class.forName(className);
        return utilClass.newInstance();
    }
}
