package ee.cyber.sdsb.proxyui;

import java.security.cert.X509Certificate;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.conf.globalconf.GlobalConf;
import ee.cyber.sdsb.common.conf.serverconf.ServerConfDatabaseCtx;
import ee.cyber.sdsb.common.conf.serverconf.dao.ClientDAOImpl;
import ee.cyber.sdsb.common.identifier.ClientId;

import static ee.cyber.sdsb.common.ErrorCodes.X_UNKNOWN_MEMBER;

public class ImportCertUtil {

    public static void verifyClientExists(ClientId clientId) throws Exception {
        if (clientId != null && !clientExists(clientId)) {
            throw CodedException.tr(X_UNKNOWN_MEMBER,
                    "member_not_found",
                    "Certificate issued to an unknown member '%s'", clientId);
        }
    }

    public static ClientId getClientIdForSigningCert(String instanceIdentifier,
            X509Certificate cert) throws Exception {
        return GlobalConf.getSubjectName(instanceIdentifier, cert);
    }

    private static boolean clientExists(ClientId clientId) throws Exception {
        return ClientDAOImpl.getInstance().clientExists(
                ServerConfDatabaseCtx.get().getSession(), clientId, true);
    }
}
