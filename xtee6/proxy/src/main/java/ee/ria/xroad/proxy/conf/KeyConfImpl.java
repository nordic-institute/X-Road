package ee.ria.xroad.proxy.conf;

import java.io.File;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.bouncycastle.cert.ocsp.OCSPResp;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.cert.CertChain;
import ee.ria.xroad.common.conf.globalconf.AuthKey;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.proxy.signedmessage.SignerSigningKey;
import ee.ria.xroad.signer.protocol.SignerClient;
import ee.ria.xroad.signer.protocol.dto.AuthKeyInfo;
import ee.ria.xroad.signer.protocol.dto.MemberSigningInfo;
import ee.ria.xroad.signer.protocol.message.GetAuthKey;
import ee.ria.xroad.signer.protocol.message.GetMemberSigningInfo;
import ee.ria.xroad.signer.protocol.message.GetOcspResponses;
import ee.ria.xroad.signer.protocol.message.GetOcspResponsesResponse;
import ee.ria.xroad.signer.protocol.message.SetOcspResponses;

import static ee.ria.xroad.common.ErrorCodes.X_CANNOT_CREATE_SIGNATURE;
import static ee.ria.xroad.common.util.CertUtils.getCertHashes;
import static ee.ria.xroad.common.util.CryptoUtils.*;

/**
 * Encapsulates KeyConf related functionality.
 */
@Slf4j
class KeyConfImpl implements KeyConfProvider {

    KeyConfImpl() {
    }

    @Override
    public SigningCtx getSigningCtx(ClientId clientId) {
        log.debug("Retrieving signing info for member '{}'", clientId);
        try {
            MemberSigningInfo signingInfo = SignerClient.execute(
                    new GetMemberSigningInfo(clientId));

            SigningCtx ctx = createSigningCtx(clientId,
                    signingInfo.getKeyId(),
                    signingInfo.getCert().getCertificateBytes());
            return ctx;
        } catch (Exception e) {
            throw new CodedException(X_CANNOT_CREATE_SIGNATURE,
                    "Failed to get signing info for member '%s': %s",
                    clientId, e);
        }
    }

    @Override
    public AuthKey getAuthKey() {
        PrivateKey pkey = null;
        CertChain certChain = null;
        try {
            SecurityServerId serverId = ServerConf.getIdentifier();
            log.debug("Retrieving authentication info for security "
                    + "server '{}'", serverId);

            AuthKeyInfo keyInfo =
                    SignerClient.execute(new GetAuthKey(serverId));

            pkey = loadAuthPrivateKey(keyInfo);
            if (pkey == null) {
                log.warn("Failed to read authentication key");
            }

            certChain = getAuthCertChain(serverId.getXRoadInstance(),
                    keyInfo.getCert().getCertificateBytes());
            if (certChain == null) {
                log.warn("Failed to read authentication certificate");
            }
        } catch (Exception e) {
            log.error("Failed to get authentication key", e);
        }

        AuthKey authKey = new AuthKey(certChain, pkey);
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
                        new GetOcspResponses(new String[] {certHash}));

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

    static SigningCtx createSigningCtx(ClientId subject, String keyId,
            byte[] certBytes) throws Exception {
        X509Certificate cert = readCertificate(certBytes);
        return new SigningCtxImpl(subject, new SignerSigningKey(keyId), cert);
    }

    static CertChain getAuthCertChain(String instanceIdentifier,
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

    static PrivateKey loadAuthPrivateKey(AuthKeyInfo keyInfo) throws Exception {
        String alias = keyInfo.getAlias();
        File keyStoreFile = new File(keyInfo.getKeyStoreFileName());
        char[] password = keyInfo.getPassword();

        log.trace("Loading authentication key from key store '{}'",
                keyStoreFile);

        KeyStore ks = loadPkcs12KeyStore(keyStoreFile, password);

        PrivateKey privateKey = (PrivateKey) ks.getKey(alias, password);
        if (privateKey == null) {
            log.warn("Failed to read authentication key");
        }

        return privateKey;
    }
}
