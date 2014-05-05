package ee.cyber.sdsb.asyncdb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.conf.AbstractXmlConf;
import ee.cyber.sdsb.common.conf.serverconf.ObjectFactory;
import ee.cyber.sdsb.common.conf.serverconf.ServerConfType;

public class AsyncSenderConf extends AbstractXmlConf<ServerConfType> {

    private static final Logger LOG = LoggerFactory
            .getLogger(AsyncSenderConf.class);

    static final int DEFAULT_BASE_DELAY = 300;
    static final int DEFAULT_MAX_DELAY = 1800;
    static final int DEFAULT_MAX_SENDERS = 1000;

    public static AsyncSenderConf getInstance() {
        try {
            return new AsyncSenderConf(SystemProperties.getServerConfFile());
        } catch (Exception e) {
            LOG.error(
                    "Failed to load server configuration file for AsyncSender,"
                            + " using default parameters", e);
            return new AsyncSenderConf();
        }
    }

    private AsyncSenderConf() {
    }

    private AsyncSenderConf(String filename) {
        super(ObjectFactory.class, filename);
    }

    public int getBaseDelay() {
        if (isConfFileReadSuccessfully()) {
            return confType.getAsyncSender().getBaseDelay();
        }

        return DEFAULT_BASE_DELAY;
    }

    public int getMaxDelay() {
        if (isConfFileReadSuccessfully()) {
            return confType.getAsyncSender().getMaxDelay();
        }

        return DEFAULT_MAX_DELAY;
    }

    public int getMaxSenders() {
        if (isConfFileReadSuccessfully()) {
            return confType.getAsyncSender().getMaxSenders();
        }

        return DEFAULT_MAX_SENDERS;
    }

    private boolean isConfFileReadSuccessfully() {
        return confType != null && confType.getAsyncSender() != null;
    }
}
