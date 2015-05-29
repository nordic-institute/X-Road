package ee.ria.xroad.proxyui;

import java.security.cert.X509Certificate;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.serverconf.ServerConfDatabaseCtx;
import ee.ria.xroad.common.conf.serverconf.dao.ClientDAOImpl;
import ee.ria.xroad.common.identifier.ClientId;

import static ee.ria.xroad.common.ErrorCodes.X_UNKNOWN_MEMBER;

/**
 * Contains utility methods for importing certificates.
 */
public final class ImportCertUtil {

    private ImportCertUtil() {
    }

    /**
     * Verififes whether a client with the given ID exists in the database.
     * @param clientId the client ID
     */
    public static void verifyClientExists(ClientId clientId) {
        if (clientId != null && !clientExists(clientId)) {
            throw CodedException.tr(X_UNKNOWN_MEMBER,
                    "member_not_found",
                    "Certificate issued to an unknown member '%s'", clientId);
        }
    }

    /**
     * Returns the given certificate owner's client ID.
     * @param instanceIdentifier instance identifier of the owner
     * @param cert the certificate
     * @return certificate owner's client ID
     * @throws Exception if any errors occur
     */
    public static ClientId getClientIdForSigningCert(String instanceIdentifier,
            X509Certificate cert) throws Exception {
        return GlobalConf.getSubjectName(instanceIdentifier, cert);
    }

    private static boolean clientExists(ClientId clientId) {
        return new ClientDAOImpl().clientExists(
                ServerConfDatabaseCtx.get().getSession(), clientId, true);
    }
}
