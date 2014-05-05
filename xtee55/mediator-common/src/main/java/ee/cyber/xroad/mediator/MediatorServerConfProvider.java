package ee.cyber.xroad.mediator;

import java.util.List;

import ee.cyber.sdsb.common.conf.serverconf.ServerConfProvider;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.ServiceId;

public interface MediatorServerConfProvider extends ServerConfProvider {

    boolean isSdsbService(ServiceId serviceId);

    String getBackendURL(ServiceId serviceId);

    String getBackendURL(ClientId clientId);

    List<String> getAdapterWSDLUrls(ClientId clientId);
}
