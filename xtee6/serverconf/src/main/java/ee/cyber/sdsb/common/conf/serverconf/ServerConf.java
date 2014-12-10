package ee.cyber.sdsb.common.conf.serverconf;

import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import ee.cyber.sdsb.common.conf.InternalSSLKey;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.SecurityCategoryId;
import ee.cyber.sdsb.common.identifier.SecurityServerId;
import ee.cyber.sdsb.common.identifier.ServiceId;

/** Configuration of the current proxy server.
 */
@Slf4j
public class ServerConf {

    private static volatile ServerConfProvider instance = null;

    /**
     * Returns the singleton instance of the configuration.
     */
    protected static ServerConfProvider getInstance() {
        if (instance == null) {
            instance = new ServerConfImpl();
        }

        return instance;
    }

    /**
     * Reloads the configuration with given configuration instance.
     */
    public static void reload(ServerConfProvider conf) {
        log.trace("reload({})", conf.getClass());

        instance = conf;
    }

    // ------------------------------------------------------------------------

    /**
     * Returns the identifier of this Security Server.
     */
    public static SecurityServerId getIdentifier() {
        log.trace("getIdentifier()");

        return getInstance().getIdentifier();
    }

    /**
     * Returns true, if service with the given identifier exists in
     * the configuration.
     */
    public static boolean serviceExists(ServiceId service) {
        log.trace("serviceExists({})", service);

        return getInstance().serviceExists(service);
    }

    /**
     * Returns true, if member <code>sender</code> is allowed
     * to invoke service <code>serviceName</code>
     */
    public static boolean isQueryAllowed(ClientId sender, ServiceId service) {
        log.trace("isQueryAllowed({}, {})", sender, service);

        return getInstance().isQueryAllowed(sender, service);
    }

    /**
     * If the service is disabled, returns notice about this event.
     * If the service is enabled, returns null.
     */
    public static String getDisabledNotice(ServiceId service) {
        log.trace("getDisabledNotice({})", service);

        return getInstance().getDisabledNotice(service);
    }

    /**
     * Return URL for corresponding to service provider for given
     * service name.
     */
    public static String getServiceAddress(ServiceId service) {
        log.trace("getServiceAddress({})", service);

        return getInstance().getServiceAddress(service);
    }

    /**
     * Return service timeout in seconds.
     */
    public static int getServiceTimeout(ServiceId service) {
        log.trace("getServiceTimeout({})", service);

        return getInstance().getServiceTimeout(service);
    }

    /**
     * Return all the services offered by a service provider.
     */
    public static List<ServiceId> getAllServices(ClientId serviceProvider) {
        log.trace("getAllServices({})", serviceProvider);

        return getInstance().getAllServices(serviceProvider);
    }

    /**
     * Return all the services by a service provider that the caller
     * has permission to invoke.
     */
    public static List<ServiceId> getAllowedServices(ClientId serviceProvider,
            ClientId client) {
        log.trace("getAllowedServices({}, {})", serviceProvider, client);

        return getInstance().getAllowedServices(serviceProvider, client);
    }

    /**
     * Returns set of security category codes required by this service.
     */
    public static Collection<SecurityCategoryId> getRequiredCategories(
            ServiceId service) {
        log.trace("getRequiredCategories({})", service);

        return getInstance().getRequiredCategories(service);
    }

    /**
     * Returns all members.
     */
    public static List<ClientId> getMembers() throws Exception {
        log.trace("getMembers()");

        return getInstance().getMembers();
    }

    /**
     * Returns the status of the member or null if member is not found.
     */
    public static String getMemberStatus(ClientId memberId) {
        log.trace("getMemberStatus({})", memberId);

        return getInstance().getMemberStatus(memberId);
    }

    /**
     * Returns whether the SSL certificate of the service provider is verified.
     */
    public static boolean isSslAuthentication(ServiceId service) {
        log.trace("isSslAuthentication({})", service);

        return getInstance().isSslAuthentication(service);
    }

    /**
     * Returns the list of certificates that are allowed to be used to
     * authenticate the client information system.
     */
    public static List<X509Certificate> getIsCerts(ClientId client)
            throws Exception {
        log.trace("getIsCerts({})", client);

        return getInstance().getIsCerts(client);
    }

    /**
     * Returns list of URLs for the Time-stamping providers configured
     * in this security server.
     */
    public static List<String> getTspUrl() {
        log.trace("getTspUrl()");

        return getInstance().getTspUrl();
    }

    /**
     * Returns the internal SSL key.
     */
    public static InternalSSLKey getSSLKey() throws Exception {
        log.trace("getInternalSSLKey()");

        return getInstance().getSSLKey();
    }

    /**
     * Returns the authentication method for the client information system.
     */
    public static IsAuthentication getIsAuthentication(ClientId client) {
        log.trace("getIsAuthentication({})", client);

        return getInstance().getIsAuthentication(client);
    }
}
