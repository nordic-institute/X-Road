package ee.cyber.xroad.mediator;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.conf.serverconf.ServerConfProvider;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

/**
 * Mediator server configuration.
 */
@Slf4j
public final class MediatorServerConf extends ServerConf {


    /**
     * @param serviceId the X-Road 6.0 service identifier
     * @return true if this service identifier corresponds to a X-Road 6.0 service
     */
    public static boolean isXroadService(ServiceId serviceId) {
        log.trace("isXroadService({})", serviceId);

        return instance().isXroadService(serviceId);
    }

    /**
     * @param serviceId the X-Road 6.0 service identifier
     * @return the backend URL of the service with the given identifier
     */
    public static String getBackendURL(ServiceId serviceId) {
        log.trace("getBackendURL({})", serviceId);

        return instance().getBackendURL(serviceId);
    }

    /**
     * @param clientId the X-Road 6.0 client identifier
     * @return the first backend URL found for the client with the given identifier
     */
    public static String getBackendURL(ClientId clientId) {
        log.trace("getBackendURL({})", clientId);

        return instance().getBackendURL(clientId);
    }

    /**
     * @param clientId the X-Road 6.0 client identifier
     * @return all adapter WSDL URLs for the client with the given identifier
     */
    public static List<String> getAdapterWSDLUrls(ClientId clientId) {
        log.trace("getAdapterWSDLUrls({})", clientId);

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
