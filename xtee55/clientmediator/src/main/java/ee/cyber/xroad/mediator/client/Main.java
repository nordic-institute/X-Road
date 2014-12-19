package ee.cyber.xroad.mediator.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.SystemPropertiesLoader;
import ee.cyber.xroad.mediator.MediatorSystemProperties;

import static ee.cyber.sdsb.common.SystemProperties.CONF_FILE_USER_LOCAL;
import static ee.cyber.xroad.mediator.MediatorSystemProperties.CONF_FILE_CLIENT_MEDIATOR;
import static ee.cyber.xroad.mediator.MediatorSystemProperties.CONF_FILE_MEDIATOR_COMMON;

public class Main {

    static {
        new SystemPropertiesLoader(MediatorSystemProperties.PREFIX) {
            @Override
            public void load() {
                load(CONF_FILE_MEDIATOR_COMMON);
                load(CONF_FILE_CLIENT_MEDIATOR);
                load(CONF_FILE_USER_LOCAL);
            }
        };
    }

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
