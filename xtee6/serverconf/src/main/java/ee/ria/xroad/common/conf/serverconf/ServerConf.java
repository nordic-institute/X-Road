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

import lombok.extern.slf4j.Slf4j;

import ee.ria.xroad.common.conf.InternalSSLKey;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityCategoryId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;

/**
 * Configuration of the current proxy server.
 */
@Slf4j
public class ServerConf {

    private static volatile ServerConfProvider instance = null;

    protected ServerConf() {
    }

    /**
     * Returns the singleton instance of the configuration.
     */
    protected static ServerConfProvider getInstance() {
        if (instance == null) {
            instance = new CachingServerConfImpl();
        }

        return instance;
    }

    /**
     * Reloads the configuration with given configuration instance.
     * @param conf the new configuration implementation
     */
    public static void reload(ServerConfProvider conf) {
        log.trace("reload({})", conf.getClass());

        instance = conf;
    }

    // ------------------------------------------------------------------------

    /**
     * @return the identifier of this Security Server.
     */
    public static SecurityServerId getIdentifier() {
        log.trace("getIdentifier()");

        return getInstance().getIdentifier();
    }

    /**
     * @param service the service identifier
     * @return true, if service with the given identifier exists in
     * the configuration.
     */
    public static boolean serviceExists(ServiceId service) {
        log.trace("serviceExists({})", service);

        return getInstance().serviceExists(service);
    }

    /**
     * @param sender the sender identifier
     * @param service the service identifier
     * @return true, if member <code>sender</code> is allowed
     * to invoke service <code>serviceName</code>
     */
    public static boolean isQueryAllowed(ClientId sender, ServiceId service) {
        log.trace("isQueryAllowed({}, {})", sender, service);

        return getInstance().isQueryAllowed(sender, service);
    }

    /**
     * @param service the service identifier
     * @return if the service is disabled, returns notice about this event.
     * If the service is enabled, returns null.
     */
    public static String getDisabledNotice(ServiceId service) {
        log.trace("getDisabledNotice({})", service);

        return getInstance().getDisabledNotice(service);
    }

    /**
     * @param service the service identifier
     * @return URL for corresponding to service provider for given
     * service name.
     */
    public static String getServiceAddress(ServiceId service) {
        log.trace("getServiceAddress({})", service);

        return getInstance().getServiceAddress(service);
    }

    /**
     * @param service the service identifier
     * @return service timeout in seconds.
     */
    public static int getServiceTimeout(ServiceId service) {
        log.trace("getServiceTimeout({})", service);

        return getInstance().getServiceTimeout(service);
    }

    /**
     * @param serviceProvider the service provider identifier
     * @return all the services offered by a service provider.
     */
    public static List<ServiceId> getAllServices(ClientId serviceProvider) {
        log.trace("getAllServices({})", serviceProvider);

        return getInstance().getAllServices(serviceProvider);
    }

    /**
     * @param serviceProvider the service provider identifier
     * @param client the client identifier
     * @return all the services by a service provider that the caller
     * has permission to invoke.
     */
    public static List<ServiceId> getAllowedServices(ClientId serviceProvider,
            ClientId client) {
        log.trace("getAllowedServices({}, {})", serviceProvider, client);

        return getInstance().getAllowedServices(serviceProvider, client);
    }

    /**
     * @param service the service identifier
     * @return set of security category codes required by this service.
     */
    public static Collection<SecurityCategoryId> getRequiredCategories(
            ServiceId service) {
        log.trace("getRequiredCategories({})", service);

        return getInstance().getRequiredCategories(service);
    }

    /**
     * @return all members.
     * @throws Exception if an error occurs
     */
    public static List<ClientId> getMembers() throws Exception {
        log.trace("getMembers()");

        return getInstance().getMembers();
    }

    /**
     * @param memberId the member identifier
     * @return the status of the member or null if member is not found.
     */
    public static String getMemberStatus(ClientId memberId) {
        log.trace("getMemberStatus({})", memberId);

        return getInstance().getMemberStatus(memberId);
    }

    /**
     * @param service the service identifier
     * @return whether the SSL certificate of the service provider is verified.
     */
    public static boolean isSslAuthentication(ServiceId service) {
        log.trace("isSslAuthentication({})", service);

        return getInstance().isSslAuthentication(service);
    }

    /**
     * @param client the client identifier
     * @return the list of certificates that are allowed to be used to
     * authenticate the client information system.
     * @throws Exception if an error occurs
     */
    public static List<X509Certificate> getIsCerts(ClientId client)
            throws Exception {
        log.trace("getIsCerts({})", client);

        return getInstance().getIsCerts(client);
    }

    /**
     * @return list of URLs for the Time-stamping providers configured
     * in this security server.
     */
    public static List<String> getTspUrl() {
        log.trace("getTspUrl()");

        return getInstance().getTspUrl();
    }

    /**
     * @return the internal SSL key.
     * @throws Exception if an error occurs
     */
    public static InternalSSLKey getSSLKey() throws Exception {
        log.trace("getInternalSSLKey()");

        return getInstance().getSSLKey();
    }

    /**
     * @param client the client identifier
     * @return the authentication method for the client information system.
     */
    public static IsAuthentication getIsAuthentication(ClientId client) {
        log.trace("getIsAuthentication({})", client);

        return getInstance().getIsAuthentication(client);
    }
}
