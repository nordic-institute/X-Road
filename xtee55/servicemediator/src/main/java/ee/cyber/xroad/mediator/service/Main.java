package ee.cyber.xroad.mediator.service;

import lombok.extern.slf4j.Slf4j;

import ee.cyber.xroad.mediator.MediatorSystemProperties;
import ee.ria.xroad.common.SystemPropertiesLoader;

import static ee.cyber.xroad.mediator.MediatorSystemProperties.CONF_FILE_MEDIATOR_COMMON;
import static ee.cyber.xroad.mediator.MediatorSystemProperties.CONF_FILE_SERVICE_MEDIATOR;

/**
 * Service mediator program.
 */
@Slf4j
public final class Main {

    static {
        SystemPropertiesLoader.create().withCommon().load();
        SystemPropertiesLoader.create(MediatorSystemProperties.PREFIX)
            .withLocal()
            .with(CONF_FILE_MEDIATOR_COMMON)
            .with(CONF_FILE_SERVICE_MEDIATOR)
            .load();
    }

    private Main() {
    }

    /**
     * Main program access point.
     * @param args command-line arguments
     * @throws Exception in case of any errors
     */
    public static void main(String[] args) throws Exception {
        log.info("ServiceMediator starting...");

        try {
            ServiceMediator mediator = new ServiceMediator();

            try {
                mediator.start();
                mediator.join();
            } finally {
                log.info("ServiceMediator shutting down...");
                mediator.stop();
            }
        } catch (Exception e) {
            log.error("ServiceMediator crashed with exception:", e);
        }
    }

}
