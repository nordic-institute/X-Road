package ee.cyber.sdsb.signer.tokenmanager;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.bouncycastle.cert.ocsp.OCSPResp;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.signer.model.Cert;
import ee.cyber.sdsb.signer.model.CertRequest;
import ee.cyber.sdsb.signer.model.Key;
import ee.cyber.sdsb.signer.model.Token;
import ee.cyber.sdsb.signer.protocol.dto.CertRequestInfo;
import ee.cyber.sdsb.signer.protocol.dto.CertificateInfo;
import ee.cyber.sdsb.signer.protocol.dto.KeyInfo;
import ee.cyber.sdsb.signer.protocol.dto.KeyUsageInfo;
import ee.cyber.sdsb.signer.protocol.dto.TokenInfo;
import ee.cyber.sdsb.signer.protocol.dto.TokenStatusInfo;
import ee.cyber.sdsb.signer.tokenmanager.module.SoftwareModuleType;
import ee.cyber.sdsb.signer.tokenmanager.token.TokenType;
import ee.cyber.sdsb.signer.util.SignerUtil;
import ee.cyber.sdsb.signer.util.TokenAndKey;

import static ee.cyber.sdsb.common.ErrorCodes.X_WRONG_CERT_USAGE;
import static ee.cyber.sdsb.signer.util.ExceptionHelper.*;
import static java.util.Collections.unmodifiableList;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TokenManager {

    static volatile List<Token> currentTokens = new ArrayList<>();

    static boolean initialized;

    public static void init() throws Exception {
        TokenConf.reload();

        currentTokens = new ArrayList<>(TokenConf.getInstance().getTokens());

        initialized = true;
    }

    public static synchronized void saveToConf() throws Exception {
        log.trace("persist()");

        if (initialized) {
            TokenConf.getInstance().save(currentTokens);
        }
    }

    // ------------------------------------------------------------------------

    public static synchronized List<TokenInfo> listTokens() {
        List<TokenInfo> tokenInfos = new ArrayList<>();
        for (Token token : currentTokens) {
            tokenInfos.add(token.toDTO());
        }

        return unmodifiableList(tokenInfos);
    }

    public static List<KeyInfo> listKeys(String tokenId) {
        return unmodifiableList(findTokenInfo(tokenId).getKeyInfo());
    }

    public static synchronized TokenInfo createToken(TokenType tokenType) {
        Token token = new Token(tokenType.getModuleType(), tokenType.getId());
        token.setModuleId(tokenType.getModuleType());
        token.setReadOnly(tokenType.isReadOnly());
        token.setSerialNumber(tokenType.getSerialNumber());
        token.setLabel(tokenType.getLabel());
        token.setSlotIndex(tokenType.getSlotIndex());
        token.setFriendlyName(getDefaultFriendlyName(tokenType));
        token.setBatchSigningEnabled(tokenType.isBatchSigningEnabled());
        token.setAvailable(true);

        currentTokens.add(token);

        return token.toDTO();
    }

    public static TokenInfo findTokenInfo(String tokenId) {
        TokenInfo tokenInfo = getTokenInfo(tokenId);
        if (tokenInfo == null) {
            throw tokenNotFound(tokenId);
        }

        return tokenInfo;
    }

    public static synchronized TokenInfo getTokenInfo(String tokenId) {
        log.trace("getTokenInfo({})", tokenId);

        for (Token token : currentTokens) {
            if (token.getId().equals(tokenId)) {
                return token.toDTO();
            }
        }

        return null;
    }

    public static synchronized TokenAndKey findTokenAndKey(String keyId) {
        log.trace("findTokenAndKey({})", keyId);

        for (Token token : currentTokens) {
            for (Key key : token.getKeys()) {
                if (key.getId().equals(keyId)) {
                    return new TokenAndKey(token.getId(), key.toDTO());
                }
            }
        }

        throw keyNotFound(keyId);
    }

    public static synchronized String findTokenIdForKeyId(String keyId) {
        for (Token token : currentTokens) {
            for (Key key : token.getKeys()) {
                if (key.getId().equals(keyId)) {
                    return token.getId();
                }
            }
        }

        throw keyNotFound(keyId);
    }

    public static synchronized String getSoftwareTokenId() {
        for (Token token : currentTokens) {
            if (token.getType().equals(SoftwareModuleType.TYPE)) {
                return token.getId();
            }
        }

        return null;
    }

    public static synchronized String getModuleId(String tokenId) {
        for (Token token : currentTokens) {
            if (token.getId().equals(tokenId)) {
                return token.getModuleId();
            }
        }

        return null;
    }

    public static KeyInfo findKeyInfo(String keyId) {
        KeyInfo keyInfo = getKeyInfo(keyId);
        if (keyInfo == null) {
            throw keyNotFound(keyId);
        }

        return keyInfo;
    }

    public static synchronized KeyInfo getKeyInfo(String keyId) {
        log.trace("getKeyInfo({})", keyId);

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
        log.trace("getKeyInfo({})", clientId);

        for (Token token : currentTokens) {
            if (!token.isActive() || !token.isAvailable()) {
                // Ignore inactive (not usable) tokens
                log.debug("Ignoring inactive token '{}'", token.getId());
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
                        log.debug("Found key '{}' for client '{}'",
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
            throw certWithIdNotFound(certId);
        }

        return certificateInfo;
    }

    public static synchronized CertificateInfo getCertificateInfo(
            String certId) {
        log.trace("getCertificateInfo({})", certId);

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
        log.trace("getCertificateInfoForCertHash({})", certHash);

        for (Token token : currentTokens) {
            for (Key key : token.getKeys()) {
                for (Cert cert : key.getCerts()) {
                    if (certHash.equals(cert.getHash())) {
                        return cert.toDTO();
                    }
                }
            }
        }

        return null;
    }

    public static synchronized X509Certificate getCertificateForCertHash(
            String certHash) {
        log.trace("getCertificateForCertHash({})", certHash);

        for (Token token : currentTokens) {
            for (Key key : token.getKeys()) {
                for (Cert cert : key.getCerts()) {
                    if (certHash.equals(cert.getHash())) {
                        return cert.getCertificate();
                    }
                }
            }
        }

        return null;
    }

    public static synchronized List<CertificateInfo> getAllCerts() {
        log.trace("getAllCerts()");

        List<CertificateInfo> allCerts = new ArrayList<>();

        for (Token token : currentTokens) {
            for (Key key : token.getKeys()) {
                for (Cert cert : key.getCerts()) {
                    allCerts.add(cert.toDTO());
                }
            }
        }

        return allCerts;
    }

    public static synchronized void setOcspResponse(String certHash,
            OCSPResp response) {
        log.trace("setOcspResponse({})", certHash);

        for (Token token : currentTokens) {
            for (Key key : token.getKeys()) {
                for (Cert cert : key.getCerts()) {
                    if (certHash.equals(cert.getHash())) {
                        cert.setOcspResponse(response);
                    }
                }
            }
        }
    }

    public static synchronized CertRequestInfo getCertRequestInfo(String keyId,
            ClientId memberId) {
        log.trace("getCertRequestInfo({}, {})", keyId, memberId);

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
        log.trace("getKeyInfoForCertHash({})", certHash);

        for (Token token : currentTokens) {
            for (Key key : token.getKeys()) {
                for (Cert cert : key.getCerts()) {
                    if (certHash.equals(cert.getHash())) {
                        return key.toDTO();
                    }
                }
            }
        }

        return null;
    }

    public static synchronized KeyInfo getKeyInfoForCertId(String certId) {
        log.trace("getKeyInfoForCertId({})", certId);

        for (Token token : currentTokens) {
            for (Key key : token.getKeys()) {
                for (Cert cert : key.getCerts()) {
                    if (cert.getId().equals(certId)) {
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
        log.trace("isTokenAvailable({})", tokenId);

        return findToken(tokenId).isAvailable();
    }

    public static synchronized boolean isTokenActive(String tokenId) {
        log.trace("isTokenActive({})", tokenId);

        return findToken(tokenId).isActive();
    }

    public static synchronized void setTokenAvailable(TokenType tokenType,
            boolean available) {
        String tokenId = tokenType.getId();

        log.trace("setTokenAvailable({}, {})", tokenId, available);

        Token token = findToken(tokenId);
        token.setAvailable(available);
        token.setModuleId(tokenType.getModuleType());
    }

    public static synchronized void setTokenAvailable(String tokenId,
            boolean available) {
        log.trace("setTokenAvailable({}, {})", tokenId, available);

        findToken(tokenId).setAvailable(available);
    }

    public static synchronized void setTokenActive(String tokenId,
            boolean active) {
        log.trace("setTokenActive({}, {})", tokenId, active);

        findToken(tokenId).setActive(active);
    }

    public static synchronized void setTokenFriendlyName(String tokenId,
            String friendlyName) {
        log.trace("setTokenFriendlyName({}, {})", tokenId, friendlyName);

        findToken(tokenId).setFriendlyName(friendlyName);
    }

    public static synchronized TokenStatusInfo getTokenStatus(String tokenId) {
        log.trace("getTokenStatus({})", tokenId);

        return findToken(tokenId).getStatus();
    }

    public static synchronized void setTokenStatus(String tokenId,
            TokenStatusInfo status) {
        log.trace("setTokenStatus({}, {})", tokenId, status);

        findToken(tokenId).setStatus(status);
    }

    public static synchronized void setKeyAvailable(String keyId,
            boolean available) {
        log.trace("setKeyAvailable({}, {})", keyId, available);

        findKey(keyId).setAvailable(available);
    }

    public static synchronized boolean isKeyAvailable(String keyId) {
        log.trace("isKeyAvailable()");

        return findKey(keyId).isAvailable();
    }

    public static synchronized void setKeyFriendlyName(String keyId,
            String friendlyName) {
        log.trace("setKeyFriendlyName({}, {})", keyId, friendlyName);

        findKey(keyId).setFriendlyName(friendlyName);
    }

    public static synchronized void setKeyUsage(String keyId,
            KeyUsageInfo keyUsage) {
        log.trace("setKeyUsage({}, {})", keyId, keyUsage);

        findKey(keyId).setUsage(keyUsage);
    }

    public static synchronized KeyInfo addKey(String tokenId, String keyId,
            String publicKeyBase64) {
        log.trace("addKey({}, {})", tokenId, keyId);

        Token token = findToken(tokenId);

        Key key = new Key(token, keyId);
        key.setPublicKey(publicKeyBase64);

        token.addKey(key);

        return key.toDTO();
    }

    public static synchronized boolean removeKey(String keyId) {
        log.trace("removeKey({})", keyId);

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
        log.trace("setPublicKey({}, {})", keyId, publicKeyBase64);

        findKey(keyId).setPublicKey(publicKeyBase64);
    }

    public static synchronized void addCert(String keyId, byte[] certBytes) {
        log.trace("addCert({})", keyId);

        Key key = findKey(keyId);

        Cert cert = new Cert(SignerUtil.randomId());
        cert.setCertificate(certBytes);

        key.addCert(cert);
    }

    public static synchronized void addCert(String keyId,
            CertificateInfo certInfo) {
        log.trace("addCert({})", keyId);

        Key key = findKey(keyId);

        Cert cert = new Cert(certInfo.getId());
        cert.setActive(certInfo.isActive());
        cert.setCertificate(certInfo.getCertificateBytes());
        cert.setOcspResponse(certInfo.getOcspBytes());
        cert.setMemberId(certInfo.getMemberId());
        cert.setSavedToConfiguration(certInfo.isSavedToConfiguration());
        cert.setStatus(certInfo.getStatus());

        key.addCert(cert);
    }

    public static synchronized void setCertActive(String certId,
            boolean active) {
        log.trace("setCertActive({}, {})", certId, active);

        findCert(certId).setActive(active);
    }

    public static synchronized void setCertStatus(String certId,
            String status) {
        log.trace("setCertStatus({}, {})", certId, status);

        findCert(certId).setStatus(status);
    }

    public static synchronized void setCertSavedToConfiguration(String certId,
            boolean saved) {
        log.trace("setCertSavedToConfiguration({}, {})", certId, saved);

        findCert(certId).setSavedToConfiguration(saved);
    }

    public static synchronized boolean removeCert(String certId) {
        log.trace("removeCert({})", certId);

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
        log.trace("addCertRequest({}, {})", keyId, memberId);

        Key key = findKey(keyId);

        if (key.getUsage() != null && key.getUsage() != keyUsage) {
            throw CodedException.tr(X_WRONG_CERT_USAGE,
                    "cert_request_wrong_usage",
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
                log.warn("Certificate request (memberId: {}, " +
                        "subjectName: {}) already exists", memberId,
                        subjectName);
                return certRequest.getId();
            }
        }

        String certId = SignerUtil.randomId();
        key.addCertRequest(new CertRequest(certId, memberId, subjectName));

        log.info("Added new certificate request (memberId: {}, " +
                "subjectId: {}) under key {}",
                new Object[] { memberId, subjectName, keyId });

        return certId;
    }

    public static synchronized String removeCertRequest(String certReqId) {
        log.trace("removeCertRequest({})", certReqId);

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
        log.trace("isBatchSigningEnabled({})", tokenId);

        return findToken(tokenId).isBatchSigningEnabled();
    }

    // ------------------------------------------------------------------------

    private static Token findToken(String tokenId) {
        log.trace("findToken({})", tokenId);

        for (Token token : currentTokens) {
            if (token.getId().equals(tokenId)) {
                return token;
            }
        }

        throw tokenNotFound(tokenId);
    }

    private static Key findKey(String keyId) {
        log.trace("findKey({})", keyId);

        for (Token token : currentTokens) {
            for (Key key : token.getKeys()) {
                if (key.getId().equals(keyId)) {
                    return key;
                }
            }
        }

        throw keyNotFound(keyId);
    }

    private static Cert findCert(String certId) {
        log.trace("findCert({})", certId);

        for (Token token : currentTokens) {
            for (Key key : token.getKeys()) {
                for (Cert cert : key.getCerts()) {
                    if (cert.getId().equals(certId)) {
                        return cert;
                    }
                }
            }
        }

        throw certWithIdNotFound(certId);
    }

    private static String getDefaultFriendlyName(TokenType tokenType) {
        String name = tokenType.getModuleType();

        if (tokenType.getSerialNumber() != null) {
            name += "-" + tokenType.getSerialNumber();
        }

        if (tokenType.getLabel() != null) {
            name += "-" + tokenType.getLabel();
        }

        if (tokenType.getSlotIndex() != null) {
            name += "-" + tokenType.getSlotIndex();
        }

        return name;
    }
}
