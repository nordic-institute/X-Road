package ee.cyber.sdsb.proxy.conf;

import java.io.OutputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.conf.AuthKey;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.util.FileContentChangeChecker;
import ee.cyber.sdsb.proxy.signedmessage.SignerSigningKey;
import ee.cyber.sdsb.signer.protocol.SignerClient;
import ee.cyber.sdsb.signer.protocol.dto.AuthKeyInfo;
import ee.cyber.sdsb.signer.protocol.dto.CertificateInfo;
import ee.cyber.sdsb.signer.protocol.dto.MemberSigningInfo;
import ee.cyber.sdsb.signer.protocol.message.GetAuthKey;
import ee.cyber.sdsb.signer.protocol.message.GetMemberCerts;
import ee.cyber.sdsb.signer.protocol.message.GetMemberCertsResponse;
import ee.cyber.sdsb.signer.protocol.message.GetMemberSigningInfo;

import static ee.cyber.sdsb.common.ErrorCodes.*;
import static ee.cyber.sdsb.common.util.CryptoUtils.loadKeyStore;
import static ee.cyber.sdsb.common.util.CryptoUtils.readCertificate;

/**
 * Encapsulates KeyConf related functionality.
 *
 * TODO: Better code, optimizations and caching
 */
public class KeyConfImpl implements KeyConfProvider {

    private static final Logger LOG =
            LoggerFactory.getLogger(KeyConfImpl.class);

    private final FileContentChangeChecker keyConfChangeChecker;

    private final Map<ClientId, SigningCtx> signingCtxCache = new HashMap<>();
    private final Map<ClientId, List<X509Certificate>> memberCertsCache =
            new HashMap<>();
    private AuthKey authKey = new AuthKey(null, null);

    protected KeyConfImpl() throws Exception {
        keyConfChangeChecker =
                new FileContentChangeChecker(SystemProperties.getKeyConfFile());
    }

    @Override
    public SigningCtx getSigningCtx(ClientId clientId) {
        if (hasChanged() || !signingCtxCache.containsKey(clientId)) {
            LOG.debug("Retrieving signing info for member '{}'", clientId);
            try {
                MemberSigningInfo signingInfo =
                        SignerClient.execute(
                                new GetMemberSigningInfo(clientId));

                X509Certificate cert = readCertificate(signingInfo.getCert());
                SigningCtx ctx = new SigningCtxImpl(
                        new SignerSigningKey(signingInfo.getKeyId()), cert);
                signingCtxCache.put(clientId, ctx);
                return ctx;
            } catch (Exception e) {
                throw new CodedException(X_CANNOT_CREATE_SIGNATURE,
                        "Failed to get signing info for member '%s': %s",
                        clientId, e);
            }
        } else if (signingCtxCache.containsKey(clientId)) {
            return signingCtxCache.get(clientId);
        }

        throw new CodedException(
               X_UNKNOWN_MEMBER, "Unknown member '%s'", clientId);
    }

    @Override
    public List<X509Certificate> getMemberCerts(ClientId clientId)
            throws Exception {
        if (hasChanged() || !memberCertsCache.containsKey(clientId)) {
            try {
                GetMemberCertsResponse response = SignerClient.execute(
                        new GetMemberCerts(clientId));
                List<X509Certificate> certs = new ArrayList<>();
                for (CertificateInfo certInfo : response.getCerts()) {
                    if (certInfo.isActive()) { // XXX: only active certs?
                        certs.add(readCertificate(
                                certInfo.getCertificateBytes()));
                    }
                }

                memberCertsCache.put(clientId, certs);
                return certs;
            } catch (Exception e) {
                throw translateWithPrefix(X_UNKNOWN_MEMBER, e);
            }
        } else if (memberCertsCache.containsKey(clientId)) {
            return memberCertsCache.get(clientId);
        }

        throw new CodedException(
                X_UNKNOWN_MEMBER, "Unknown member '%s'", clientId);
    }

    @Override
    public AuthKey getAuthKey() {
        if (hasChanged() || authKey.getKey() == null) {
            try {
                AuthKeyInfo keyInfo = SignerClient.execute(
                        new GetAuthKey(ServerConf.getIdentifier()));

                String alias = keyInfo.getAlias();
                String keyStoreFile = keyInfo.getKeyStoreFileName();
                char[] password = keyInfo.getPassword();

                LOG.trace("Loading authentication key from key store '{}'",
                        keyStoreFile);

                KeyStore ks = loadKeyStore("pkcs12", keyStoreFile, password);
                PrivateKey pkey = (PrivateKey) ks.getKey(alias, password);
                X509Certificate cert =
                        readCertificate(keyInfo.getCertificateBytes());

                if (cert == null) {
                    LOG.warn("Failed to read authentication certificate");
                }

                if (pkey == null) {
                    LOG.warn("Failed to read authentication key");
                }

                authKey = new AuthKey(cert, pkey);
            } catch (Exception e) {
                LOG.error("Failed to get authentication key", e);
            }
        }

        return authKey;
    }

    @Override
    public X509Certificate getOcspSignerCert() throws Exception {
        // TODO Implement, ask from Signer
        return null;
    }

    @Override
    public PrivateKey getOcspRequestKey(X509Certificate member)
            throws Exception {
        // TODO Implement, ask from Signer
        return null;
    }

    @Override
    public boolean hasChanged() {
        try {
            boolean changed = keyConfChangeChecker.hasChanged();
            LOG.debug("KeyConf has{} changed!", !changed ? " not" : "");
            return changed;
        } catch (Exception e) {
            LOG.error("Failed to check if key conf has changed", e);
            return true;
        }
    }

    @Override
    public void save() throws Exception {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void save(OutputStream out) throws Exception {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void load(String fileName) throws Exception {
        throw new RuntimeException("Not implemented");
    }

}
