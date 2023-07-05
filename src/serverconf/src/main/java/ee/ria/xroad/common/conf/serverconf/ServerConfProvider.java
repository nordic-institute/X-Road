/**
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.common.conf.serverconf;

import ee.ria.xroad.common.conf.InternalSSLKey;
import ee.ria.xroad.common.conf.serverconf.model.DescriptionType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.metadata.Endpoint;
import ee.ria.xroad.common.metadata.RestServiceDetailsListType;

import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Provides API for implementing configuration providers.
 */
public interface ServerConfProvider {

    /**
     * @return the identifier of this Security Server.
     */
    SecurityServerId.Conf getIdentifier();

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
     * @return RestServiceDetailsListType containing list of REST services
     */
    RestServiceDetailsListType getRestServices(ClientId serviceProvider);

    /**
     * @param serviceProvider the service provider identifier
     * @param client the client identifier
     * @return RestServiceDetailsListType containing list of allowed REST services
     */
    RestServiceDetailsListType getAllowedRestServices(ClientId serviceProvider, ClientId client);

    /**
     * @param serviceProvider the service provider identifier
     * @return all the services offered by a service provider.
     */
    List<ServiceId.Conf> getAllServices(ClientId serviceProvider);

    /**
     * @param serviceProvider the service provider identifier
     * @return all the services offered by a service provider.
     */
    List<ServiceId.Conf> getServicesByDescriptionType(ClientId serviceProvider, DescriptionType descriptionType);

    /**
     * @param serviceProvider the service provider identifier
     * @param client the client identifier
     * @return all the services by a service provider that the caller
     * has permission to invoke.
     */
    List<ServiceId.Conf> getAllowedServices(ClientId serviceProvider, ClientId client);

    /**
     * @param serviceProvider the service provider identifier
     * @param client the client identifier
     * @return all the services by a service provider that the caller
     * has permission to invoke filtered by description type
     */
    List<ServiceId.Conf> getAllowedServicesByDescriptionType(ClientId serviceProvider, ClientId client,
                                                        DescriptionType descriptionType);

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
     * List all known certificates that are allowed to be used to authenticate
     * the client information system.
     */
    List<X509Certificate> getAllIsCerts();

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
    List<ClientId.Conf> getMembers() throws Exception;

    /**
     * @param memberId the member identifier
     * @return the status of the member or null of member is not found
     */
    String getMemberStatus(ClientId memberId);

    /**
     * @param sender the sender identifier
     * @param service the service identifier
     * @param method the request method (can be null)
     * @param path the request path (can be null)
     * @return true, if member <code>sender</code> is allowed
     * to invoke service <code>serviceName</code>
     */
    boolean isQueryAllowed(ClientId sender, ServiceId service, String method, String path);

    /**
     * @return list of URLs for the Time-stamping providers configured
     * in this security server.
     */
    List<String> getTspUrl();

    /**
     * @param service the service identifier
     * @return the type of the service as {@link DescriptionType}
     */
    DescriptionType getDescriptionType(ServiceId service);

    /**
     * @param service the service identifier
     * @return the service description url
     */
    String getServiceDescriptionURL(ServiceId service);

    /**
     * @param service the service identifier
     * @return list of endpoints
     */
    List<Endpoint> getServiceEndpoints(ServiceId service);

    /**
     * Log serverconf statistics
     */
    default void logStatistics() {
        //NOP
    }

    /**
     * Clear configuration cache
     */
    default void clearCache() {
        // by default there is no cache to clear
    }

    /**
     * @return true if this provider is capable of providing configuration
     */
    default boolean isAvailable() {
        return true;
    }
}
