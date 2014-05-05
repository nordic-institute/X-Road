package ee.cyber.xroad.mediator;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.conf.serverconf.ServerConf;
import ee.cyber.sdsb.common.conf.serverconf.ServerConfProvider;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.ServiceId;

public final class MediatorServerConf extends ServerConf {

    private static final Logger LOG =
            LoggerFactory.getLogger(MediatorServerConf.class);

    public static boolean isSdsbService(ServiceId serviceId) {
        LOG.trace("isSdsbService({})", serviceId);

        return instance().isSdsbService(serviceId);
    }

    public static String getBackendURL(ServiceId serviceId) {
        LOG.trace("getBackendURL({})", serviceId);

        return instance().getBackendURL(serviceId);
    }

    public static String getBackendURL(ClientId clientId) {
        LOG.trace("getBackendURL({})", clientId);

        return instance().getBackendURL(clientId);
    }

    public static List<String> getAdapterWSDLUrls(ClientId clientId) {
        LOG.trace("getAdapterWSDLUrls({})", clientId);

        return instance().getAdapterWSDLUrls(clientId);
    }

    private static MediatorServerConfProvider instance() {
        ServerConfProvider instance = getInstance();
        if (instance instanceof MediatorServerConfProvider) {
            return (MediatorServerConfProvider) instance;
        }

        throw new RuntimeException("Server conf instance must be subclass of "
                + MediatorServerConfProvider.class);
    }
}
