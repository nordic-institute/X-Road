package ee.ria.xroad.centerui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.ria.xroad.commonui.UIServices;
import ee.ria.xroad.signer.protocol.SignerClient;

/**
 * Contains the UI actor system instance.
 */
public final class CenterUIServices {

    private static final Logger LOG =
            LoggerFactory.getLogger(CenterUIServices.class);

    private static UIServices instance;

    private CenterUIServices() {
    }

    private static void init() throws Exception {
        if (instance == null) {
            instance = new UIServices("CenterUI", "centerui");
        }
    }

    /**
     * Initializes the UI actor system.
     * @throws Exception in case of any errors
     */
    public static void start() throws Exception {
        LOG.info("start()");

        init();

        SignerClient.init(instance.getActorSystem());
    }

    /**
     * Stops the UI actor system.
     * @throws Exception in case of any errors
     */
    public static void stop() throws Exception {
        instance.stop();
    }
}
