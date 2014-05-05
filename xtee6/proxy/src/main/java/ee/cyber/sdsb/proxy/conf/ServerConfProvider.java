package ee.cyber.sdsb.proxy.conf;

import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;

import org.bouncycastle.cert.ocsp.OCSPResp;

import ee.cyber.sdsb.common.conf.ConfProvider;
import ee.cyber.sdsb.common.conf.ServerConfCommonProvider;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.SecurityCategoryId;
import ee.cyber.sdsb.common.identifier.ServiceId;

/** Provides API for implementing configuration providers. */
public interface ServerConfProvider
        extends ServerConfCommonProvider, ConfProvider {

    /** Returns a list of certificates for which to retrieve OCSP responses. */
    List<X509Certificate> getCertsForOcsp() throws Exception;

    /** Returns certificates for all members. */
    List<X509Certificate> getMemberCerts() throws Exception;

    /** Returns the OCSP server response for the given certificate,
     * or null, if no response is available for that certificate. */
    OCSPResp getOcspResponse(X509Certificate cert) throws Exception;

    /** Returns true, if we have cached OCSP response for this certificate */
    boolean isCachedOcspResponse(String certHash) throws Exception;

    /** Updates the existing OCSP response or stores the OCSP response,
     * if it does not exist for the given certificate.*/
    void setOcspResponse(String certHash, OCSPResp response)
            throws Exception;

    /**
     * Returns true, if service with the given identifier exists in
     * the configuration.
     */
    boolean serviceExists(ServiceId service);

    /** Returns true, if member <code>sender</code> is allowed
     * to invoke service <code>serviceName</code>
     */
    boolean isQueryAllowed(ClientId sender, ServiceId service);

    /**
     * If the service is disabled, returns notice about this event.
     * If the service is enabled, returns null.
     */
    String getDisabledNotice(ServiceId service);

    /**
     * Returns set of security category codes required by this service.
     */
    Collection<SecurityCategoryId> getRequiredCategories(ServiceId service);

    /**
     * Returns the URL of the GlobalConf distributor.
     */
    String getGlobalConfDistributorUrl();

    /**
     * Returns the certificate used to verify signed GlobalConf.
     */
    X509Certificate getGlobalConfVerificationCert() throws Exception;

    /**
     * Returns list of URLs for the Time-stamping providers configured
     * in this security server.
     */
    List<String> getTspUrl();
}
