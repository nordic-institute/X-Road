package ee.ria.xroad.proxy.messagelog;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Set;

import ee.ria.xroad.common.conf.InternalSSLKey;
import ee.ria.xroad.common.conf.serverconf.IsAuthentication;
import ee.ria.xroad.common.conf.serverconf.ServerConfProvider;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityCategoryId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;

/**
 * Implementation of ServerConfProvider that does nothing but return nulls. You
 * can extend this class and override only the more interesting methods.
 */
public class EmptyServerConf implements ServerConfProvider {

    @Override
    public boolean serviceExists(ServiceId service) {
        return true;
    }

    @Override
    public boolean isQueryAllowed(ClientId sender, ServiceId service) {
        return true;
    }

    @Override
    public String getDisabledNotice(ServiceId service) {
        return null;
    }

    @Override
    public String getServiceAddress(ServiceId service) {
        return null;
    }

    @Override
    public Set<SecurityCategoryId> getRequiredCategories(ServiceId service) {
        return emptySet();
    }

    @Override
    public SecurityServerId getIdentifier() {
        return null;
    }

    @Override
    public int getServiceTimeout(ServiceId service) {
        return 300;
    }

    @Override
    public IsAuthentication getIsAuthentication(ClientId client) {
        return null;
    }

    @Override
    public List<X509Certificate> getIsCerts(ClientId client) throws Exception {
        return emptyList();
    }

    @Override
    public InternalSSLKey getSSLKey() throws Exception {
        return null;
    }

    @Override
    public List<String> getTspUrl() {
        return emptyList();
    }

    @Override
    public boolean isSslAuthentication(ServiceId service) {
        return false;
    }

    @Override
    public List<ClientId> getMembers() throws Exception {
        return emptyList();
    }

    @Override
    public List<ServiceId> getAllServices(ClientId serviceProvider) {
        return emptyList();
    }

    @Override
    public List<ServiceId> getAllowedServices(ClientId serviceProvider,
            ClientId client) {
        return emptyList();
    }

    @Override
    public String getMemberStatus(ClientId memberId) {
        return null;
    }

}
