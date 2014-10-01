package ee.cyber.sdsb.proxyui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.util.JobManager;
import ee.cyber.sdsb.commonui.UIServices;
import ee.cyber.sdsb.signer.protocol.SignerClient;

public class ProxyUIServices {

    private static final Logger LOG =
            LoggerFactory.getLogger(ProxyUIServices.class);

    private static UIServices uiActorSystem;
    private static JobManager jobManager;

    public static void start() throws Exception {
        LOG.info("start()");

        if (uiActorSystem == null) {
            uiActorSystem = new UIServices("ProxyUI", "proxyui");
        }

        SignerClient.init(uiActorSystem.getActorSystem());

        if (jobManager == null) {
            jobManager = new JobManager();
            jobManager.registerRepeatingJob(GlobalConfChecker.class, 30);
            jobManager.registerRepeatingJob(IdentifierMappingChecker.class, 30);
        }

        jobManager.start();
    }

    public static void stop() throws Exception {
        if (uiActorSystem != null) {
            uiActorSystem.stop();
        }

        if (jobManager != null) {
            jobManager.stop();
        }
    }
}
