/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
