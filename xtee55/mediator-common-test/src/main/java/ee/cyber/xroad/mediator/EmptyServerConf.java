package ee.cyber.xroad.mediator;

import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;

import ee.ria.xroad.common.conf.InternalSSLKey;
import ee.ria.xroad.common.conf.serverconf.IsAuthentication;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityCategoryId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;

/**
 * Empty test serverconf implementation.
 */
public class EmptyServerConf implements MediatorServerConfProvider {

    @Override
    public boolean isXroadService(ServiceId serviceId) {
        return false;
    }

    @Override
    public String getBackendURL(ServiceId serviceId) {
        return null;
    }

    @Override
    public String getBackendURL(ClientId clientId) {
        return null;
    }

    @Override
    public SecurityServerId getIdentifier() {
        return null;
    }

    @Override
    public String getServiceAddress(ServiceId service) {
        return null;
    }

    @Override
    public int getServiceTimeout(ServiceId service) {
        return 0;
    }

    @Override
    public IsAuthentication getIsAuthentication(ClientId client) {
        return null;
    }

    @Override
    public List<X509Certificate> getIsCerts(ClientId client) throws Exception {
        return null;
    }

    @Override
    public InternalSSLKey getSSLKey() throws Exception {
        return null;
    }

    @Override
    public boolean isSslAuthentication(ServiceId service) {
        return false;
    }

    @Override
    public boolean serviceExists(ServiceId service) {
        return false;
    }

    @Override
    public String getDisabledNotice(ServiceId service) {
        return null;
    }

    @Override
    public List<String> getAdapterWSDLUrls(ClientId clientId) {
        return null;
    }

    @Override
    public List<ClientId> getMembers() throws Exception {
        return null;
    }

    @Override
    public Collection<SecurityCategoryId> getRequiredCategories(
            ServiceId service) {
        return null;
    }

    @Override
    public List<String> getTspUrl() {
        return null;
    }

    @Override
    public boolean isQueryAllowed(ClientId client, ServiceId service) {
        return false;
    }

    @Override
    public List<ServiceId> getAllServices(ClientId arg0) {
        return null;
    }

    @Override
    public List<ServiceId> getAllowedServices(ClientId arg0, ClientId arg1) {
        return null;
    }

    @Override
    public String getMemberStatus(ClientId arg0) {
        return null;
    }
}
