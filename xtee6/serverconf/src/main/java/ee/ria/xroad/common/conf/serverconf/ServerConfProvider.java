package ee.ria.xroad.common.conf.serverconf;

import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;

import ee.ria.xroad.common.conf.InternalSSLKey;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityCategoryId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;

/**
 * Provides API for implementing configuration providers.
 */
public interface ServerConfProvider {

    /**
     * @return the identifier of this Security Server.
     */
    SecurityServerId getIdentifier();

    /**
     * @param service the service identifier
     * @return true, if service with the given identifier exists in
     * the configuration.
     */
    boolean serviceExists(ServiceId service);

    /**
     * @param service the service identifier
     * @return if the service is disabled, returns notice about this event.
     * If the service is enabled, returns null.
     */
    String getDisabledNotice(ServiceId service);

    /**
     * @param service the service identifier
     * @return URL for corresponding to service provider for given service name.
     */
    String getServiceAddress(ServiceId service);

    /**
     * @param service the service identifier
     * @return the timeout value (in seconds) for the service.
     */
    int getServiceTimeout(ServiceId service);

    /**
     * @param serviceProvider the service provider identifier
     * @return all the services offered by a service provider.
     */
    List<ServiceId> getAllServices(ClientId serviceProvider);

    /**
     * @param serviceProvider the service provider identifier
     * @param client the client identifier
     * @return all the services by a service provider that the caller
     * has permission to invoke.
     */
    List<ServiceId> getAllowedServices(ClientId serviceProvider, ClientId client);

    /**
     * @param client the client identifier
     * @return the authentication method for the client information system.
     */
    IsAuthentication getIsAuthentication(ClientId client);

    /**
     * @param client the client identifier
     * @return the list of certificates that are allowed to be used to
     * authenticate the client information system.
     * @throws Exception if an error occurs
     */
    List<X509Certificate> getIsCerts(ClientId client) throws Exception;

    /**
     * @return the internal SSL cert-key pair.
     * @throws Exception if an error occurs
     */
    InternalSSLKey getSSLKey() throws Exception;

    /**
     * @param service the service identifier
     * @return whether the SSL certificate of the service provider is verified.
     */
    boolean isSslAuthentication(ServiceId service);

    /**
     * @return all members identifiers
     * @throws Exception if an error occurs
     */
    List<ClientId> getMembers() throws Exception;

    /**
     * @param memberId the member identifier
     * @return the status of the member or null of member is not found
     */
    String getMemberStatus(ClientId memberId);

    /**
     * @param sender the sender identifier
     * @param service the service identifier
     * @return true, if member <code>sender</code> is allowed
     * to invoke service <code>serviceName</code>
     */
    boolean isQueryAllowed(ClientId sender, ServiceId service);

    /**
     * @param service the service identifier
     * @return set of security category codes required by this service.
     */
    Collection<SecurityCategoryId> getRequiredCategories(ServiceId service);

    /**
     * @return list of URLs for the Time-stamping providers configured
     * in this security server.
     */
    List<String> getTspUrl();
}
