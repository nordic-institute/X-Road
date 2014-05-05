package ee.cyber.sdsb.common.conf;

import java.security.cert.X509Certificate;
import java.util.List;

import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.SecurityServerId;
import ee.cyber.sdsb.common.identifier.ServiceId;

public interface ServerConfCommonProvider {

    /**
     * Returns the identifier of this Security Server.
     */
    SecurityServerId getIdentifier();

    /**
     * Return URL for corresponding to service provider for given
     * service name.
     */
    String getServiceAddress(ServiceId service);

    /**
     * Return the timeout value (in seconds) for the service.
     */
    int getServiceTimeout(ServiceId service);

    /**
     * Returns the authentication method for the client information system.
     */
    IsAuthentication getIsAuthentication(ClientId client);

    /**
     * Returns the list of certificates that are allowed to be used to
     * authenticate the client information system.
     */
    List<X509Certificate> getIsCerts(ClientId client) throws Exception;

    /**
     * Returns the internal SSL cert-key pair.
     */
    InternalSSLKey getSSLKey() throws Exception;
}
