package ee.cyber.xroad.mediator.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        LOG.info("ServiceMediator starting...");

        try {
            ServiceMediator mediator = new ServiceMediator();

            try {
                mediator.start();
                mediator.join();
            } finally {
                LOG.info("ServiceMediator shutting down...");
                mediator.stop();
            }
        } catch (Exception e) {
            LOG.error("ServiceMediator crashed with exception:", e);
        }
    }

}
