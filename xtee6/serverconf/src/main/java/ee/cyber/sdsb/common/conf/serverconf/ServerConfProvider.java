package ee.cyber.sdsb.common.conf.serverconf;

import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;

import ee.cyber.sdsb.common.conf.InternalSSLKey;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.SecurityCategoryId;
import ee.cyber.sdsb.common.identifier.SecurityServerId;
import ee.cyber.sdsb.common.identifier.ServiceId;

/**
 * Provides API for implementing configuration providers.
 */
public interface ServerConfProvider {

    /**
     * @return the identifier of this Security Server.
     */
    SecurityServerId getIdentifier();

    /**
     * @return true, if service with the given identifier exists in
     * the configuration.
     */
    boolean serviceExists(ServiceId service);

    /**
     * @return if the service is disabled, returns notice about this event.
     * If the service is enabled, returns null.
     */
    String getDisabledNotice(ServiceId service);

    /**
     * @return URL for corresponding to service provider for given service name.
     */
    String getServiceAddress(ServiceId service);

    /**
     * @return the timeout value (in seconds) for the service.
     */
    int getServiceTimeout(ServiceId service);

    /**
     * @return all the services offered by a service provider.
     */
    List<ServiceId> getAllServices(ClientId serviceProvider);

    /**
     * @return all the services by a service provider that the caller
     * has permission to invoke.
     */
    List<ServiceId> getAllowedServices(ClientId serviceProvider, ClientId client);

    /**
     * @return the authentication method for the client information system.
     */
    IsAuthentication getIsAuthentication(ClientId client);

    /**
     * @return the list of certificates that are allowed to be used to
     * authenticate the client information system.
     */
    List<X509Certificate> getIsCerts(ClientId client) throws Exception;

    /**
     * @return the internal SSL cert-key pair.
     */
    InternalSSLKey getSSLKey() throws Exception;

    /**
     * @return whether the SSL certificate of the service provider is verified.
     */
    boolean isSslAuthentication(ServiceId service);

    /**
     * @return all members identifiers
     */
    List<ClientId> getMembers() throws Exception;

    /**
     * @return the status of the member or null of member is not found
     */
    String getMemberStatus(ClientId memberId);

    /**
     * @return true, if member <code>sender</code> is allowed
     * to invoke service <code>serviceName</code>
     */
    boolean isQueryAllowed(ClientId sender, ServiceId service);

    /**
     * @return set of security category codes required by this service.
     */
    Collection<SecurityCategoryId> getRequiredCategories(ServiceId service);

    /**
     * @return list of URLs for the Time-stamping providers configured
     * in this security server.
     */
    List<String> getTspUrl();
}
