package ee.cyber.sdsb.proxy.conf;

import java.io.File;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.bouncycastle.cert.ocsp.OCSPResp;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.cert.CertChain;
import ee.cyber.sdsb.common.conf.globalconf.AuthKey;
import ee.cyber.sdsb.common.conf.globalconf.GlobalConf;
import ee.cyber.sdsb.common.conf.serverconf.ServerConf;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.SecurityServerId;
import ee.cyber.sdsb.common.util.FileContentChangeChecker;
import ee.cyber.sdsb.proxy.signedmessage.SignerSigningKey;
import ee.cyber.sdsb.signer.protocol.SignerClient;
import ee.cyber.sdsb.signer.protocol.dto.AuthKeyInfo;
import ee.cyber.sdsb.signer.protocol.dto.MemberSigningInfo;
import ee.cyber.sdsb.signer.protocol.message.GetAuthKey;
import ee.cyber.sdsb.signer.protocol.message.GetMemberSigningInfo;
import ee.cyber.sdsb.signer.protocol.message.GetOcspResponses;
import ee.cyber.sdsb.signer.protocol.message.GetOcspResponsesResponse;
import ee.cyber.sdsb.signer.protocol.message.SetOcspResponses;

import static ee.cyber.sdsb.common.ErrorCodes.X_CANNOT_CREATE_SIGNATURE;
import static ee.cyber.sdsb.common.ErrorCodes.X_UNKNOWN_MEMBER;
import static ee.cyber.sdsb.common.util.CertUtils.getCertHashes;
import static ee.cyber.sdsb.common.util.CryptoUtils.*;

/**
 * Encapsulates KeyConf related functionality.
 */
@Slf4j
public class KeyConfImpl implements KeyConfProvider {

    private final FileContentChangeChecker keyConfChangeChecker;

    private final Map<ClientId, SigningCtx> signingCtxCache = new HashMap<>();

    private AuthKey authKey = new AuthKey(null, null);

    protected KeyConfImpl() throws Exception {
        keyConfChangeChecker =
                new FileContentChangeChecker(SystemProperties.getKeyConfFile());
    }

    @Override
    public SigningCtx getSigningCtx(ClientId clientId) {
        if (hasChanged() || !signingCtxCache.containsKey(clientId)) {
            log.debug("Retrieving signing info for member '{}'", clientId);
            try {
                MemberSigningInfo signingInfo = SignerClient.execute(
                        new GetMemberSigningInfo(clientId));

                SigningCtx ctx = createSigningCtx(clientId,
                        signingInfo.getKeyId(),
                        signingInfo.getCert().getCertificateBytes());

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
    public AuthKey getAuthKey() {
        if (hasChanged() || authKey.getKey() == null) {
            PrivateKey pkey = null;
            CertChain certChain = null;
            try {
                SecurityServerId serverId = ServerConf.getIdentifier();
                log.debug("Retrieving authentication info for security "
                        + "server '{}'", serverId);

                AuthKeyInfo keyInfo =
                        SignerClient.execute(new GetAuthKey(serverId));

                String alias = keyInfo.getAlias();
                File keyStoreFile = new File(keyInfo.getKeyStoreFileName());
                char[] password = keyInfo.getPassword();

                log.trace("Loading authentication key from key store '{}'",
                        keyStoreFile);

                KeyStore ks = loadPkcs12KeyStore(keyStoreFile, password);

                pkey = (PrivateKey) ks.getKey(alias, password);
                if (pkey == null) {
                    log.warn("Failed to read authentication key");
                }

                certChain = getAuthCertChain(serverId.getSdsbInstance(),
                        keyInfo.getCert().getCertificateBytes());
                if (certChain == null) {
                    log.warn("Failed to read authentication certificate");
                }
            } catch (Exception e) {
                log.error("Failed to get authentication key", e);
            } finally {
                authKey = new AuthKey(certChain, pkey);
            }
        }

        return authKey;
    }

    @Override
    public OCSPResp getOcspResponse(X509Certificate cert) throws Exception {
        return getOcspResponse(calculateCertHexHash(cert));
    }

    @Override
    public OCSPResp getOcspResponse(String certHash) throws Exception {
        GetOcspResponsesResponse response =
                SignerClient.execute(
                        new GetOcspResponses(new String[] { certHash }));

        for (String base64Encoded : response.getBase64EncodedResponses()) {
            return base64Encoded != null
                    ? new OCSPResp(decodeBase64(base64Encoded)) : null;
        }

        return null;
    }

    @Override
    public List<OCSPResp> getOcspResponses(List<X509Certificate> certs)
            throws Exception {
        GetOcspResponsesResponse response =
                SignerClient.execute(
                        new GetOcspResponses(getCertHashes(certs)));

        List<OCSPResp> ocspResponses = new ArrayList<>();
        for (String base64Encoded : response.getBase64EncodedResponses()) {
            if (base64Encoded != null) {
                ocspResponses.add(new OCSPResp(decodeBase64(base64Encoded)));
            } else {
                ocspResponses.add(null);
            }
        }

        return ocspResponses;
    }

    @Override
    public void setOcspResponses(List<X509Certificate> certs,
            List<OCSPResp> responses) throws Exception {
        String[] base64EncodedResponses = new String[responses.size()];

        for (int i = 0; i < responses.size(); i++) {
            base64EncodedResponses[i] =
                    encodeBase64(responses.get(i).getEncoded());
        }

        SignerClient.execute(new SetOcspResponses(getCertHashes(certs),
                base64EncodedResponses));
    }

    boolean hasChanged() {
        try {
            boolean changed = keyConfChangeChecker.hasChanged();
            log.trace("KeyConf has{} changed!", !changed ? " not" : "");
            return changed;
        } catch (Exception e) {
            log.error("Failed to check if key conf has changed", e);
            return true;
        }
    }

    private static SigningCtx createSigningCtx(ClientId subject, String keyId,
            byte[] certBytes) throws Exception {
        X509Certificate cert = readCertificate(certBytes);
        return new SigningCtxImpl(subject, new SignerSigningKey(keyId), cert);
    }

    private static CertChain getAuthCertChain(String instanceIdentifier,
            byte[] authCertBytes) throws Exception {
        X509Certificate authCert = readCertificate(authCertBytes);
        try {
            return GlobalConf.getCertChain(instanceIdentifier, authCert);
        } catch (Exception e) {
            log.error("Failed to get cert chain for certificate "
                    + authCert.getSubjectDN(), e);
        }

        return null;
    }
}
