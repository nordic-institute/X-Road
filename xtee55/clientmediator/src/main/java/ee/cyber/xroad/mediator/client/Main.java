package ee.cyber.xroad.mediator.client;

import lombok.extern.slf4j.Slf4j;

import ee.cyber.xroad.mediator.MediatorSystemProperties;
import ee.ria.xroad.common.SystemPropertiesLoader;

import static ee.cyber.xroad.mediator.MediatorSystemProperties.CONF_FILE_CLIENT_MEDIATOR;

/**
 * ClientMediator main program.
 */
@Slf4j
public final class Main {

    static {
        SystemPropertiesLoader.create().withCommon().load();
        SystemPropertiesLoader.create(MediatorSystemProperties.PREFIX)
            .withLocal()
            .with(CONF_FILE_CLIENT_MEDIATOR)
            .load();
    }

    private Main() {
    }

    /**
     * Main program entry point.
     * @param args command-line arguments
     * @throws Exception in case of any errors
     */
    public static void main(String[] args) throws Exception {
        log.info("ClientMediator starting...");

        ClientMediator mediator = new ClientMediator();
        try {
            mediator.start();
            mediator.join();
        } finally {
            log.info("ClientMediator shutting down...");
            mediator.stop();
        }
    }

}
