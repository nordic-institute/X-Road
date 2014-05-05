package ee.cyber.xroad.mediator;

import java.util.ArrayList;
import java.util.List;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.conf.serverconf.ServerConfImpl;
import ee.cyber.sdsb.common.conf.serverconf.model.ClientType;
import ee.cyber.sdsb.common.conf.serverconf.model.ServiceType;
import ee.cyber.sdsb.common.conf.serverconf.model.WsdlType;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.ServiceId;

import static ee.cyber.sdsb.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.cyber.sdsb.common.ErrorCodes.X_UNKNOWN_SERVICE;
import static ee.cyber.xroad.mediator.BackendTypes.SDSB;
import static ee.cyber.xroad.mediator.BackendTypes.XROADV5;

public class MediatorServerConfImpl extends ServerConfImpl
        implements MediatorServerConfProvider {

    private static final String DEFAULT_BACKEND = SDSB;

    @Override
    public boolean isSdsbService(ServiceId serviceId) {
        WsdlType wsdl = getWsdl(serviceId);
        if (wsdl == null) {
            throw new CodedException(X_UNKNOWN_SERVICE,
                    "Service '%s' not found", serviceId);
        }

        String backendType = wsdl.getBackend() != null
                ? wsdl.getBackend() : DEFAULT_BACKEND;
        if (SDSB.equalsIgnoreCase(backendType)) {
            return true;
        } else if (XROADV5.equalsIgnoreCase(backendType)) {
            return false;
        }

        throw new CodedException(X_INTERNAL_ERROR,
                "Unsupported backend type '%s'", backendType);
    }

    @Override
    public String getBackendURL(ServiceId serviceId) {
        ServiceType service = getService(serviceId);
        if (service == null) {
            return null;
        }

        String backendUrl = service.getWsdl().getBackendURL();
        return backendUrl != null ? backendUrl : service.getUrl();
    }

    @Override
    public String getBackendURL(ClientId clientId) {
        ClientType client = getClient(clientId);
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
    }

    @Override
    public List<String> getAdapterWSDLUrls(ClientId clientId) {
        ClientType client = getClient(clientId);
        if (client == null) {
            return null;
        }

        List<String> urls = new ArrayList<>();
        for (WsdlType wsdl : client.getWsdl()) {
            if (XROADV5.equalsIgnoreCase(wsdl.getBackend())) {
                urls.add(wsdl.getUrl());
            } else if (SDSB.equalsIgnoreCase(wsdl.getBackend())) {
                urls.add(wsdl.getUrl());
            }
        }

        return urls;
    }
}
