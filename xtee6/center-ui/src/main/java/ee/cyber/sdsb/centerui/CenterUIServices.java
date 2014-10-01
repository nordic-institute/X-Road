package ee.cyber.sdsb.centerui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.commonui.UIServices;
import ee.cyber.sdsb.signer.protocol.SignerClient;

public class CenterUIServices {

    private static final Logger LOG =
            LoggerFactory.getLogger(CenterUIServices.class);

    private static UIServices instance;

    private static void init() throws Exception {
        if (instance == null) {
            instance = new UIServices("CenterUI", "centerui");
        }
    }

    public static void start() throws Exception {
        LOG.info("start()");

        init();

        SignerClient.init(instance.getActorSystem());
    }

    public static void stop() throws Exception {
        instance.stop();
    }
}
