package ee.cyber.xroad.mediator.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.SystemPropertiesLoader;
import ee.cyber.xroad.mediator.MediatorSystemProperties;

import static ee.cyber.sdsb.common.SystemProperties.CONF_FILE_USER_LOCAL;
import static ee.cyber.xroad.mediator.MediatorSystemProperties.CONF_FILE_MEDIATOR_COMMON;
import static ee.cyber.xroad.mediator.MediatorSystemProperties.CONF_FILE_SERVICE_MEDIATOR;

public class Main {

    static {
        new SystemPropertiesLoader(MediatorSystemProperties.PREFIX) {
            @Override
            public void load() {
                load(CONF_FILE_MEDIATOR_COMMON);
                load(CONF_FILE_SERVICE_MEDIATOR);
                load(CONF_FILE_USER_LOCAL);
            }
        };
    }

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
