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
