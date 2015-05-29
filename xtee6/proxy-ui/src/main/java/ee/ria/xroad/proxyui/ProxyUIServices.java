package ee.ria.xroad.proxyui;

import lombok.extern.slf4j.Slf4j;

import ee.ria.xroad.common.SystemPropertiesLoader;
import ee.ria.xroad.common.util.JobManager;
import ee.ria.xroad.commonui.UIServices;
import ee.ria.xroad.signer.protocol.SignerClient;

import static ee.ria.xroad.common.SystemProperties.*;

/**
 * Contains the UI actor system instance and configuration checker jobs.
 */
@Slf4j
public final class ProxyUIServices {

    private static final int JOB_REPEAT_INTERVAL = 30;

    static {
        SystemPropertiesLoader.create().withCommonAndLocal()
            .with(CONF_FILE_PROXY)
            .with(CONF_FILE_PROXY_UI)
            .with(CONF_FILE_SIGNER)
            .load();
    }

    private static UIServices uiActorSystem;
    private static JobManager jobManager;

    private ProxyUIServices() {
    }

    /**
     * Initializes the UI actor system and registers configuration checker jobs.
     * @throws Exception in case of any errors
     */
    public static void start() throws Exception {
        log.info("start()");

        if (uiActorSystem == null) {
            uiActorSystem = new UIServices("ProxyUI", "proxyui");
        }

        SignerClient.init(uiActorSystem.getActorSystem());

        if (jobManager == null) {
            jobManager = new JobManager();
            jobManager.registerRepeatingJob(GlobalConfChecker.class,
                    JOB_REPEAT_INTERVAL);
            jobManager.registerRepeatingJob(IdentifierMappingChecker.class,
                    JOB_REPEAT_INTERVAL);
        }

        jobManager.start();
    }

    /**
     * Stops the UI actor system and running periodic jobs.
     * @throws Exception in case of any errors
     */
    public static void stop() throws Exception {
        if (uiActorSystem != null) {
            uiActorSystem.stop();
        }

        if (jobManager != null) {
            jobManager.stop();
        }
    }
}
