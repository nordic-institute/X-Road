package ee.cyber.xroad.mediator;

import java.util.List;

import ee.ria.xroad.common.conf.serverconf.ServerConfProvider;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

/**
 * Provides access to mediator server configuration.
 */
public interface MediatorServerConfProvider extends ServerConfProvider {

    /**
     * @param serviceId the X-Road 6.0 service identifier
     * @return true if this service identifier corresponds to a X-Road 6.0 service
     */
    boolean isXroadService(ServiceId serviceId);

    /**
     * @param serviceId the X-Road 6.0 service identifier
     * @return the backend URL of the service with the given identifier
     */
    String getBackendURL(ServiceId serviceId);

    /**
     * @param clientId the X-Road 6.0 client identifier
     * @return the first backend URL found for the client with the given identifier
     */
    String getBackendURL(ClientId clientId);

    /**
     * @param clientId the X-Road 6.0 client identifier
     * @return all adapter WSDL URLs for the client with the given identifier
     */
    List<String> getAdapterWSDLUrls(ClientId clientId);
}
