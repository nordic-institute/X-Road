package ee.cyber.xroad.mediator;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.ObjectUtils;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.conf.serverconf.ServerConfImpl;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.conf.serverconf.model.ServiceType;
import ee.ria.xroad.common.conf.serverconf.model.WsdlType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

import static ee.cyber.xroad.mediator.BackendTypes.XROAD;
import static ee.cyber.xroad.mediator.BackendTypes.XROADV5;
import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_UNKNOWN_SERVICE;

/**
 * Default mediator server configuration implementation.
 */
public class MediatorServerConfImpl extends ServerConfImpl
        implements MediatorServerConfProvider {

    private static final String DEFAULT_BACKEND = XROAD;

    @Override
    public boolean isXroadService(ServiceId serviceId) {
        return tx(session -> {
            WsdlType wsdl = getWsdl(session, serviceId);
            if (wsdl == null) {
                throw new CodedException(X_UNKNOWN_SERVICE,
                        "Service '%s' not found", serviceId);
            }

            String backendType = wsdl.getBackend() != null
                    ? wsdl.getBackend() : DEFAULT_BACKEND;
            if (XROAD.equalsIgnoreCase(backendType)) {
                return true;
            } else if (XROADV5.equalsIgnoreCase(backendType)) {
                return false;
            }

            throw new CodedException(X_INTERNAL_ERROR,
                    "Unsupported backend type '%s'", backendType);
        });
    }

    @Override
    public String getBackendURL(ServiceId serviceId) {
        return tx(session -> {
            ServiceType service = getService(session, serviceId);
            if (service == null) {
                return null;
            }

            String backendUrl = service.getWsdl().getBackendURL();
            return ObjectUtils.defaultIfNull(backendUrl, service.getUrl());
        });
    }

    @Override
    public String getBackendURL(ClientId clientId) {
        return tx(session -> {
            ClientType client = getClient(session, clientId);
            if (client != null && !client.getWsdl().isEmpty()) {
                WsdlType firstWsdl = client.getWsdl().get(0);

                String backendUrl = firstWsdl.getBackendURL();
                if (backendUrl == null && !firstWsdl.getService().isEmpty()) {
                    return firstWsdl.getService().get(0).getUrl();
                } else {
                    return backendUrl;
                }
            }

            return null;
        });
    }

    @Override
    public List<String> getAdapterWSDLUrls(ClientId clientId) {
        return tx(session -> {
            ClientType client = getClient(session, clientId);
            if (client == null) {
                return null;
            }

            List<String> urls = new ArrayList<>();
            for (WsdlType wsdl : client.getWsdl()) {
                if (XROADV5.equalsIgnoreCase(wsdl.getBackend())) {
                    urls.add(wsdl.getUrl());
                } else if (XROAD.equalsIgnoreCase(wsdl.getBackend())) {
                    urls.add(wsdl.getUrl());
                }
            }

            return urls;
        });
    }
}
