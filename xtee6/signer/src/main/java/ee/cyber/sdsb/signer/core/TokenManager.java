package ee.cyber.sdsb.signer.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.signer.conf.TokenConf;
import ee.cyber.sdsb.signer.core.device.DeviceTypeConf;
import ee.cyber.sdsb.signer.core.device.SoftwareDeviceType;
import ee.cyber.sdsb.signer.core.device.TokenType;
import ee.cyber.sdsb.signer.core.model.Cert;
import ee.cyber.sdsb.signer.core.model.CertRequest;
import ee.cyber.sdsb.signer.core.model.Key;
import ee.cyber.sdsb.signer.core.model.Token;
import ee.cyber.sdsb.signer.protocol.dto.CertRequestInfo;
import ee.cyber.sdsb.signer.protocol.dto.CertificateInfo;
import ee.cyber.sdsb.signer.protocol.dto.KeyInfo;
import ee.cyber.sdsb.signer.protocol.dto.KeyUsageInfo;
import ee.cyber.sdsb.signer.protocol.dto.TokenInfo;
import ee.cyber.sdsb.signer.protocol.dto.TokenStatusInfo;
import ee.cyber.sdsb.signer.util.SignerUtil;
import ee.cyber.sdsb.signer.util.TokenAndKey;

import static ee.cyber.sdsb.common.ErrorCodes.*;
import static ee.cyber.sdsb.common.util.CryptoUtils.calculateCertHexHash;

public final class TokenManager {

    public interface TokenUpdateCallback {
        void tokenRemoved(String tokenId);
        void tokenAdded(TokenInfo tokenInfo, TokenType tokenType);
    }

    static final Logger LOG = LoggerFactory.getLogger(TokenManager.class);

    static final TokenConf CONF = new TokenConf();

    static volatile List<Token> currentTokens = new ArrayList<>();

    public static synchronized void saveToConf() throws Exception {
        LOG.trace("saveToConf()");

        CONF.save(currentTokens);
    }

    public static synchronized void update(TokenUpdateCallback updateCallback)
            throws Exception {
        DeviceTypeConf.reload();

        try {
            CONF.load();
        } catch (Exception e) {
            LOG.error("Failed to load token conf", e);
        }

        List<Token> tokens = new ArrayList<>();
        TokenManagerHelper.syncTokens(tokens, updateCallback);

        currentTokens = tokens;
    }

    // ------------------------------------------------------------------------

    public static synchronized List<TokenInfo> listTokens() {
        List<TokenInfo> tokenInfos = new ArrayList<>();
        for (Token token : currentTokens) {
            tokenInfos.add(token.toDTO());
        }

        return Collections.unmodifiableList(tokenInfos);
    }

    public static List<KeyInfo> listKeys(String tokenId) {
        return Collections.unmodifiableList(
                findTokenInfo(tokenId).getKeyInfo());
    }

    public static TokenInfo findTokenInfo(String tokenId) {
        TokenInfo tokenInfo = getTokenInfo(tokenId);
        if (tokenInfo == null) {
            throw new CodedException(X_TOKEN_NOT_FOUND, "Token '%s' not found",
                    tokenId);
        }

        return tokenInfo;
    }

    public static synchronized TokenInfo getTokenInfo(String tokenId) {
        LOG.trace("getTokenInfo({})", tokenId);

        for (Token token : currentTokens) {
            if (token.getId().equals(tokenId)) {
                return token.toDTO();
            }
        }

        return null;
    }

    public static synchronized TokenAndKey findTokenAndKey(String keyId) {
        LOG.trace("findTokenAndKey({})", keyId);

        for (Token token : currentTokens) {
            for (Key key : token.getKeys()) {
                if (key.getId().equals(keyId)) {
                    return new TokenAndKey(token.getId(), key.toDTO());
                }
            }
        }

        throw new CodedException(X_KEY_NOT_FOUND, "Key '%s' not found", keyId);
    }

    public static synchronized String findTokenIdForKeyId(String keyId) {
        for (Token token : currentTokens) {
            for (Key key : token.getKeys()) {
                if (key.getId().equals(keyId)) {
                    return token.getId();
                }
            }
        }

        throw new CodedException(X_KEY_NOT_FOUND, "Key '%s' not found",
                keyId);
    }

    public static synchronized String getSoftwareTokenId() {
        for (Token token : currentTokens) {
            if (token.getType().equals(SoftwareDeviceType.TYPE)) {
                return token.getId();
            }
        }

        return null;
    }

    public static KeyInfo findKeyInfo(String keyId) {
        KeyInfo keyInfo = getKeyInfo(keyId);
        if (keyInfo == null) {
            throw new CodedException(X_KEY_NOT_FOUND, "Key '%s' not found",
                    keyId);
        }

        return keyInfo;
    }

    public static synchronized KeyInfo getKeyInfo(String keyId) {
        LOG.trace("getKeyInfo({})", keyId);

        for (Token token : currentTokens) {
            for (Key key : token.getKeys()) {
                if (key.getId().equals(keyId)) {
                    return key.toDTO();
                }
            }
        }

        return null;
    }

    public static synchronized KeyInfo getKeyInfo(ClientId clientId) {
        LOG.trace("getKeyInfo({})", clientId);

        for (Token token : currentTokens) {
            if (!token.isActive() || !token.isAvailable()) {
                // Ignore inactive (not usable) tokens
                LOG.debug("Ignoring inactive token '{}'", token.getId());
                continue;
            }

            for (Key key : token.getKeys()) {
                if (key.getUsage() == KeyUsageInfo.AUTHENTICATION) {
                    // Ignore authentication keys
                    continue;
                }

                if (!key.isAvailable()) {
                    // Ignore unavailable keys
                    continue;
                }

                for (Cert cert : key.getCerts()) {
                    if (!cert.isActive()) {
                        // Ignore inactive certificates
                        continue;
                    }

                    if (certBelongsToMember(cert.toDTO(), clientId)) {
                        LOG.debug("Found key '{}' for client '{}'",
                                key.getId(), cert.getMemberId());
                        return key.toDTO();
                    }
                }
            }
        }

        return null;
    }

    public static CertificateInfo findCertificateInfo(String certId) {
        CertificateInfo certificateInfo = getCertificateInfo(certId);
        if (certificateInfo == null) {
            throw new CodedException(X_CERT_NOT_FOUND,
                    "Certificate with id '%s' not found", certId);
        }

        return certificateInfo;
    }

    public static synchronized CertificateInfo getCertificateInfo(
            String certId) {
        LOG.trace("getCertificateInfo({})", certId);

        for (Token token : currentTokens) {
            for (Key key : token.getKeys()) {
                for (Cert cert : key.getCerts()) {
                    if (cert.getId().equals(certId)) {
                        return cert.toDTO();
                    }
                }
            }
        }

        return null;
    }

    public static synchronized CertificateInfo getCertificateInfoForCertHash(
            String certHash) {
        LOG.trace("getCertificateInfoForCertHash({})", certHash);

        for (Token token : currentTokens) {
            for (Key key : token.getKeys()) {
                for (Cert cert : key.getCerts()) {
                    if (hashEquals(certHash, cert)) {
                        return cert.toDTO();
                    }
                }
            }
        }

        return null;
    }

    public static synchronized CertRequestInfo getCertRequestInfo(String keyId,
            ClientId memberId) {
        LOG.trace("getCertRequestInfo({}, {})", keyId, memberId);

        Key key = findKey(keyId);
        for (CertRequest certReq : key.getCertRequests()) {
            if (key.getUsage() == KeyUsageInfo.AUTHENTICATION ||
                    (certReq.getMemberId() != null
                        && certReq.getMemberId().equals(memberId))) {
                return certReq.toDTO();
            }
        }

        return null;
    }

    public static synchronized KeyInfo getKeyInfoForCertHash(String certHash) {
        LOG.trace("getKeyInfoForCertHash({})", certHash);

        for (Token token : currentTokens) {
            for (Key key : token.getKeys()) {
                for (Cert cert : key.getCerts()) {
                    if (hashEquals(certHash, cert)) {
                        return key.toDTO();
                    }
                }
            }
        }

        return null;
    }

    public static boolean certBelongsToMember(CertificateInfo certInfo,
            ClientId member) {
        return member.equals(certInfo.getMemberId())
                || member.subsystemContainsMember(certInfo.getMemberId());
    }

    public static synchronized boolean isTokenAvailable(String tokenId) {
        LOG.trace("isTokenAvailable({})", tokenId);

        return findToken(tokenId).isAvailable();
    }

    public static synchronized boolean isTokenActive(String tokenId) {
        LOG.trace("isTokenActive({})", tokenId);

        return findToken(tokenId).isActive();
    }

    public static synchronized void setTokenAvailable(String tokenId,
            boolean available) {
        LOG.trace("setTokenAvailable({}, {})", tokenId, available);

        findToken(tokenId).setAvailable(available);
    }

    public static synchronized void setTokenActive(String tokenId,
            boolean active) {
        LOG.trace("setTokenActive({}, {})", tokenId, active);

        findToken(tokenId).setActive(active);
    }

    public static synchronized void setTokenFriendlyName(String tokenId,
            String friendlyName) {
        LOG.trace("setTokenFriendlyName({}, {})", tokenId, friendlyName);

        findToken(tokenId).setFriendlyName(friendlyName);
    }

    public static synchronized TokenStatusInfo getTokenStatus(String tokenId) {
        LOG.trace("getTokenStatus({})", tokenId);

        return findToken(tokenId).getStatus();
    }

    public static synchronized void setTokenStatus(String tokenId,
            TokenStatusInfo status) {
        LOG.trace("setTokenStatus({}, {})", tokenId, status);

        findToken(tokenId).setStatus(status);
    }

    public static synchronized void setKeyAvailable(String keyId,
            boolean available) {
        LOG.trace("setKeyAvailable({}, {})", keyId, available);

        findKey(keyId).setAvailable(available);
    }

    public static synchronized boolean isKeyAvailable(String keyId) {
        LOG.trace("isKeyAvailable()");

        return findKey(keyId).isAvailable();
    }

    public static synchronized void setKeyFriendlyName(String keyId,
            String friendlyName) {
        LOG.trace("setKeyFriendlyName({}, {})", keyId, friendlyName);

        findKey(keyId).setFriendlyName(friendlyName);
    }

    public static synchronized void setKeyUsage(String keyId,
            KeyUsageInfo keyUsage) {
        LOG.trace("setKeyUsage({}, {})", keyId, keyUsage);

        findKey(keyId).setUsage(keyUsage);
    }

    public static synchronized KeyInfo addKey(String tokenId, String keyId,
            String publicKeyBase64) {
        LOG.trace("addKey({}, {})", tokenId, keyId);

        Token token = findToken(tokenId);

        Key key = new Key(token, keyId);
        key.setPublicKey(publicKeyBase64);

        token.addKey(key);

        return key.toDTO();
    }

    public static synchronized boolean removeKey(String keyId) {
        LOG.trace("removeKey({})", keyId);

        for (Token token : currentTokens) {
            for (Key key : token.getKeys()) {
                if (key.getId().equals(keyId)) {
                    return token.getKeys().remove(key);
                }
            }
        }

        return false;
    }

    public static synchronized void setPublicKey(String keyId,
            String publicKeyBase64) {
        LOG.trace("setPublicKey({}, {})", keyId, publicKeyBase64);

        findKey(keyId).setPublicKey(publicKeyBase64);
    }

    public static synchronized void addCert(String keyId, byte[] certBytes) {
        LOG.trace("addCert({})", keyId);

        Key key = findKey(keyId);

        Cert cert = new Cert(SignerUtil.randomId());
        cert.setCertificateBytes(certBytes);

        key.addCert(cert);
    }

    public static synchronized void addCert(String keyId,
            CertificateInfo certInfo) {
        LOG.trace("addCert({})", keyId);

        Key key = findKey(keyId);

        Cert cert = new Cert(certInfo.getId());
        cert.setActive(certInfo.isActive());
        cert.setCertificateBytes(certInfo.getCertificateBytes());
        cert.setMemberId(certInfo.getMemberId());
        cert.setRevoked(certInfo.isRevoked());
        cert.setSavedToConfiguration(certInfo.isSavedToConfiguration());
        cert.setStatus(certInfo.getStatus());

        key.addCert(cert);
    }

    public static synchronized void setCertActive(String certId,
            boolean active) {
        LOG.trace("setCertActive({}, {})", certId, active);

        findCert(certId).setActive(active);
    }

    public static synchronized void setCertStatus(String certId,
            String status) {
        LOG.trace("setCertStatus({}, {})", certId, status);

        findCert(certId).setStatus(status);
    }

    public static synchronized void setCertSavedToConfiguration(String certId,
            boolean saved) {
        LOG.trace("setCertSavedToConfiguration({}, {})", certId, saved);

        findCert(certId).setSavedToConfiguration(saved);
    }

    public static synchronized boolean removeCert(String certId) {
        LOG.trace("removeCert({})", certId);

        for (Token token : currentTokens) {
            for (Key key : token.getKeys()) {
                for (Cert cert : key.getCerts()) {
                    if (cert.getId().equals(certId)) {
                        return key.getCerts().remove(cert);
                    }
                }
            }
        }
        return false;
    }

    public static synchronized String addCertRequest(String keyId,
            ClientId memberId, String subjectName, KeyUsageInfo keyUsage) {
        LOG.trace("addCertRequest({}, {})", keyId, memberId);

        Key key = findKey(keyId);

        if (key.getUsage() != null && key.getUsage() != keyUsage) {
            throw new CodedException(X_WRONG_CERT_USAGE,
                    "Cannot add %s certificate request to %s key", keyUsage,
                    key.getUsage());
        }

        key.setUsage(keyUsage);

        for (CertRequest certRequest : key.getCertRequests()) {
            ClientId crMember = certRequest.getMemberId();
            String crSubject = certRequest.getSubjectName();

            if ((memberId == null && crSubject.equalsIgnoreCase(subjectName))
                    || (memberId != null && memberId.equals(crMember)
                                && crSubject.equalsIgnoreCase(subjectName))) {
                LOG.warn("Certificate request (memberId: {}, " +
                        "subjectName: {}) already exists", memberId,
                        subjectName);
                return certRequest.getId();
            }
        }

        String certId = SignerUtil.randomId();
        key.addCertRequest(new CertRequest(certId, memberId, subjectName));

        LOG.info("Added new certificate request (memberId: {}, " +
                "subjectId: {}) under key {}",
                new Object[] { memberId, subjectName, keyId });

        return certId;
    }

    public static synchronized String removeCertRequest(String certReqId) {
        LOG.trace("removeCertRequest({})", certReqId);

        for (Token token : currentTokens) {
            for (Key key : token.getKeys()) {
                for (CertRequest certRequest : key.getCertRequests()) {
                    if (certRequest.getId().equals(certReqId)) {
                        if (key.getUsage() == KeyUsageInfo.AUTHENTICATION) {
                            // Authentication keys can only have one certificate request
                            key.getCertRequests().clear();
                        } else {
                            if (!key.getCertRequests().remove(certRequest)) {
                                return null;
                            }
                        }

                        return key.getId();
                    }
                }
            }
        }

        return null;
    }

    public static synchronized void setTokenInfo(String tokenId,
            Map<String, String> info) {
        findToken(tokenId).setInfo(info);
    }

    public static synchronized boolean isBatchSigningEnabled(String tokenId) {
        LOG.trace("isBatchSigningEnabled({})", tokenId);

        return findToken(tokenId).isBatchSigningEnabled();
    }

    // ------------------------------------------------------------------------

    private static Token findToken(String tokenId) {
        LOG.trace("findToken({})", tokenId);

        for (Token token : currentTokens) {
            if (token.getId().equals(tokenId)) {
                return token;
            }
        }

        throw new CodedException(X_TOKEN_NOT_FOUND, "Token '%s' not found",
                tokenId);
    }

    private static Key findKey(String keyId) {
        LOG.trace("findKey({})", keyId);

        for (Token token : currentTokens) {
            for (Key key : token.getKeys()) {
                if (key.getId().equals(keyId)) {
                    return key;
                }
            }
        }

        throw new CodedException(X_KEY_NOT_FOUND, "Key '%s' not found",
                keyId);
    }

    private static Cert findCert(String certId) {
        LOG.trace("findCert({})", certId);

        for (Token token : currentTokens) {
            for (Key key : token.getKeys()) {
                for (Cert cert : key.getCerts()) {
                    if (cert.getId().equals(certId)) {
                        return cert;
                    }
                }
            }
        }

        throw new CodedException(X_CERT_NOT_FOUND, "Cert '%s' not found",
                certId);
    }

    private static boolean hashEquals(String hash, Cert c) {
        try {
            if (hash.equals(calculateCertHexHash(c.getCertificateBytes()))) {
                return true;
            }
        } catch (Exception e) {
            LOG.error("Failed to calculate certificate hash for cert '{}'",
                    c.getId());
        }

        return false;
    }

}
