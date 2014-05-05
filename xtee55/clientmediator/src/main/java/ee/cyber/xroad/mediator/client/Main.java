package ee.cyber.xroad.mediator.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        LOG.info("ClientMediator starting...");

        ClientMediator mediator = new ClientMediator();
        try {
            mediator.start();
            mediator.join();
        } finally {
            LOG.info("ClientMediator shutting down...");
            mediator.stop();
        }
    }

}
