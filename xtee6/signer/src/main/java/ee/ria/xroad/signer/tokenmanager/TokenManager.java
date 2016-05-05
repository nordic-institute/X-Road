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
package ee.ria.xroad.signer.tokenmanager;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.ocsp.OCSPResp;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.signer.model.Cert;
import ee.ria.xroad.signer.model.CertRequest;
import ee.ria.xroad.signer.model.Key;
import ee.ria.xroad.signer.model.Token;
import ee.ria.xroad.signer.protocol.dto.CertRequestInfo;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;
import ee.ria.xroad.signer.protocol.dto.TokenStatusInfo;
import ee.ria.xroad.signer.tokenmanager.module.SoftwareModuleType;
import ee.ria.xroad.signer.tokenmanager.token.TokenType;
import ee.ria.xroad.signer.util.SignerUtil;
import ee.ria.xroad.signer.util.TokenAndKey;

import static ee.ria.xroad.common.ErrorCodes.X_WRONG_CERT_USAGE;
import static ee.ria.xroad.signer.util.ExceptionHelper.*;
import static java.util.Collections.unmodifiableList;

/**
 * Manages the current state of tokens, their keys and certificates.
 */
@Slf4j
public final class TokenManager {

    static volatile List<Token> currentTokens = new ArrayList<>();

    static boolean initialized;

    private TokenManager() {
    }

    /**
     * Initializes the manager -- loads the tokens from the token configuration.
     * @throws Exception if an error occurs
     */
    public static void init() throws Exception {
        try {
            TokenConf.getInstance().load();
        } catch (Exception e) {
            log.error("Failed to load token conf", e);
        }

        currentTokens = new ArrayList<>(TokenConf.getInstance().getTokens());

        initialized = true;
    }

    /**
     * Saves the current tokens to the configuration.
     * @throws Exception if an error occurs
     */
    public static synchronized void saveToConf() throws Exception {
        log.trace("persist()");

        if (initialized) {
            TokenConf.getInstance().save(currentTokens);
        }
    }

    // ------------------------------------------------------------------------

    /**
     * @return list of tokens
     */
    public static synchronized List<TokenInfo> listTokens() {
        return unmodifiableList(
                currentTokens.stream()
                    .map(t -> t.toDTO())
                    .collect(Collectors.toList()));
    }

    /**
     * @param tokenId the token id
     * @return list of keys for a token
     */
    public static List<KeyInfo> listKeys(String tokenId) {
        return unmodifiableList(findTokenInfo(tokenId).getKeyInfo());
    }

    /**
     * Creates a new token with specified type.
     * @param tokenType the type
     * @return the new token
     */
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

    /**
     * @param tokenId the token id
     * @return the token info DTO for the token id or
     * throws exception if not found
     */
    public static TokenInfo findTokenInfo(String tokenId) {
        TokenInfo tokenInfo = getTokenInfo(tokenId);
        if (tokenInfo != null) {
            return tokenInfo;
        }

        throw tokenNotFound(tokenId);
    }

    /**
     * @param tokenId the token id
     * @return the token info DTO for the token id or null of not found
     */
    public static synchronized TokenInfo getTokenInfo(String tokenId) {
        log.trace("getTokenInfo({})", tokenId);

        return currentTokens.stream()
                .filter(t -> t.getId().equals(tokenId))
                .map(t -> t.toDTO())
                .findFirst().orElse(null);
    }

    /**
     * @param keyId the key id
     * @return the token and key or throws exception if not found
     */
    public static synchronized TokenAndKey findTokenAndKey(String keyId) {
        log.trace("findTokenAndKey({})", keyId);

        return forKey((t, k) -> k.getId().equals(keyId),
                    (t, k) -> new TokenAndKey(t.getId(), k.toDTO()))
                .orElseThrow(() -> keyNotFound(keyId));
    }

    /**
     * @param keyId the key id
     * @return the token id for the key id or throws exception if not found
     */
    public static synchronized String findTokenIdForKeyId(String keyId) {
        log.trace("findTokenIdForKeyId({})", keyId);

        return forKey((t, k) -> k.getId().equals(keyId),
                (t, k) -> t.getId()).orElseThrow(() -> keyNotFound(keyId));
    }

    /**
     * @return the software token id
     */
    public static synchronized String getSoftwareTokenId() {
        return forToken(t -> t.getType().equals(SoftwareModuleType.TYPE),
                t -> t.getId()).orElse(null);
    }

    /**
     * @param tokenId the token id
     * @return the module id for the token id or null if not found
     */
    public static synchronized String getModuleId(String tokenId) {
        return forToken(t -> t.getId().equals(tokenId),
                t -> t.getModuleId()).orElse(null);
    }

    /**
     * @param keyId the key id
     * @return the key info for the key id or throws exception if not found
     */
    public static KeyInfo findKeyInfo(String keyId) {
        KeyInfo keyInfo = getKeyInfo(keyId);
        if (keyInfo != null) {
            return keyInfo;
        }

        throw keyNotFound(keyId);
    }

    /**
     * @param keyId the key id
     * @return the key info for the key id or null if not found
     */
    public static synchronized KeyInfo getKeyInfo(String keyId) {
        log.trace("getKeyInfo({})", keyId);

        return forKey((t, k) -> k.getId().equals(keyId),
                (t, k) -> k.toDTO()).orElse(null);
    }

    /**
     * @param clientId the client id
     * @return the list of keys for the given client id
     */
    public static synchronized List<KeyInfo> getKeyInfo(ClientId clientId) {
        log.trace("getKeyInfo({})", clientId);

        List<KeyInfo> keyInfo = new ArrayList<>();

        for (Token token : currentTokens) {
            if (!token.isActive() || !token.isAvailable()) {
                // Ignore inactive (not usable) tokens
                continue;
            }

            for (Key key : token.getKeys()) {
                if (!key.isAvailable()
                        || key.getUsage() == KeyUsageInfo.AUTHENTICATION) {
                    // Ignore authentication keys
                    continue;
                }

                for (Cert cert : key.getCerts()) {
                    if (!cert.isActive() || cert.getMemberId() == null) {
                        // Ignore inactive and invalid certificates
                        continue;
                    }

                    if (certBelongsToMember(cert.toDTO(), clientId)) {
                        log.debug("Found key '{}' for client '{}'",
                                key.getId(), cert.getMemberId());
                        keyInfo.add(key.toDTO());
                    }
                }
            }
        }

        return keyInfo;
    }

    /**
     * @param certId the certificate id
     * @return the certificate info for the certificate id or
     * throws exception if not found
     */
    public static CertificateInfo findCertificateInfo(String certId) {
        CertificateInfo certificateInfo = getCertificateInfo(certId);
        if (certificateInfo != null) {
            return certificateInfo;
        }

        throw certWithIdNotFound(certId);
    }

    /**
     * @param certId the certificate id
     * @return the certificate info for the certificate id or null if not found
     */
    public static synchronized CertificateInfo getCertificateInfo(
            String certId) {
        log.trace("getCertificateInfo({})", certId);

        return forCert((k, c) -> c.getId().equals(certId), (k, c) -> c.toDTO())
                .orElse(null);
    }

    /**
     * @param certHash the certificate hash
     * @return the certificate info for the certificate hash or null
     */
    public static synchronized CertificateInfo getCertificateInfoForCertHash(
            String certHash) {
        log.trace("getCertificateInfoForCertHash({})", certHash);

        return forCert((k, c) -> certHash.equals(c.getHash()),
                (k, c) -> c.toDTO()).orElse(null);
    }

    /**
     * @param certHash the certificate hash
     * @return the certificate for the certificate hash or null
     */
    public static synchronized X509Certificate getCertificateForCertHash(
            String certHash) {
        log.trace("getCertificateForCertHash({})", certHash);

        return forCert((k, c) -> certHash.equals(c.getHash()),
                (k, c) -> c.getCertificate()).orElse(null);
    }

    /**
     * @return all certificates
     */
    public static synchronized List<CertificateInfo> getAllCerts() {
        log.trace("getAllCerts()");

        return currentTokens.stream()
                .flatMap(t -> t.getKeys().stream())
                .flatMap(k -> k.getCerts().stream())
                .map(c -> c.toDTO())
                .collect(Collectors.toList());
    }

    /**
     * Sets the OCSP response for the certificate.
     * @param certHash the certificate hash
     * @param response the OCSP response
     */
    public static synchronized void setOcspResponse(String certHash,
            OCSPResp response) {
        log.trace("setOcspResponse({})", certHash);

        forCert((k, c) -> certHash.equals(c.getHash()),
                (k, c) -> {
                    c.setOcspResponse(response);
                    return null;
                });
    }

    /**
     * @param keyId the key id
     * @param memberId the member id
     * @return the certificate request info or null if not found
     */
    public static synchronized CertRequestInfo getCertRequestInfo(String keyId,
            ClientId memberId) {
        log.trace("getCertRequestInfo({}, {})", keyId, memberId);

        Key key = findKey(keyId);
        return key.getCertRequests().stream()
                .filter(c -> key.getUsage() == KeyUsageInfo.AUTHENTICATION
                    || memberId.equals(c.getMemberId()))
                .map(c -> c.toDTO()).findFirst().orElse(null);
    }

    /**
     * @param certHash the certificate hash
     * @return key info for the certificate hash
     */
    public static synchronized KeyInfo getKeyInfoForCertHash(String certHash) {
        log.trace("getKeyInfoForCertHash({})", certHash);

        return forCert((k, c) -> certHash.equals(c.getHash()),
                (k, c) -> k.toDTO()).orElse(null);
    }

    /**
     * @param certId the certificate id
     * @return key info for certificate id
     */
    public static synchronized KeyInfo getKeyInfoForCertId(String certId) {
        log.trace("getKeyInfoForCertId({})", certId);

        return forCert((k, c) -> c.getId().equals(certId),
                (k, c) -> k.toDTO()).orElse(null);
    }

    /**
     * @param certInfo the certificate info
     * @param member the member id
     * @return true if the cert belongs to the member
     */
    public static boolean certBelongsToMember(CertificateInfo certInfo,
            ClientId member) {
        return member.equals(certInfo.getMemberId())
                || member.subsystemContainsMember(certInfo.getMemberId());
    }

    /**
     * @param tokenId the token id
     * @return true if token is available
     */
    public static synchronized boolean isTokenAvailable(String tokenId) {
        log.trace("isTokenAvailable({})", tokenId);

        return findToken(tokenId).isAvailable();
    }

    /**
     * @param tokenId the token id
     * @return true if token is active (logged in)
     */
    public static synchronized boolean isTokenActive(String tokenId) {
        log.trace("isTokenActive({})", tokenId);

        return findToken(tokenId).isActive();
    }

    /**
     * Sets the token available.
     * @param tokenType the token type
     * @param available availability flag
     */
    public static synchronized void setTokenAvailable(TokenType tokenType,
            boolean available) {
        String tokenId = tokenType.getId();

        log.trace("setTokenAvailable({}, {})", tokenId, available);

        Token token = findToken(tokenId);
        token.setAvailable(available);
        token.setModuleId(tokenType.getModuleType());
    }

    /**
     * Sets the token available.
     * @param tokenId the token id
     * @param available availability flag
     */
    public static synchronized void setTokenAvailable(String tokenId,
            boolean available) {
        log.trace("setTokenAvailable({}, {})", tokenId, available);

        findToken(tokenId).setAvailable(available);
    }

    /**
     * Sets the token active (logged in) or not
     * @param tokenId the token id
     * @param active active flag
     */
    public static synchronized void setTokenActive(String tokenId,
            boolean active) {
        log.trace("setTokenActive({}, {})", tokenId, active);

        findToken(tokenId).setActive(active);
    }

    /**
     * Sets the token friendly name.
     * @param tokenId token id
     * @param friendlyName the friendly name
     */
    public static synchronized void setTokenFriendlyName(String tokenId,
            String friendlyName) {
        log.trace("setTokenFriendlyName({}, {})", tokenId, friendlyName);

        findToken(tokenId).setFriendlyName(friendlyName);
    }

    /**
     * @param tokenId the token if
     * @return the token status info
     */
    public static synchronized TokenStatusInfo getTokenStatus(String tokenId) {
        log.trace("getTokenStatus({})", tokenId);

        return findToken(tokenId).getStatus();
    }

    /**
     * Sets the token status info
     * @param tokenId the token id
     * @param status the status
     */
    public static synchronized void setTokenStatus(String tokenId,
            TokenStatusInfo status) {
        log.trace("setTokenStatus({}, {})", tokenId, status);

        findToken(tokenId).setStatus(status);
    }

    /**
     * Sets the key availability.
     * @param keyId the key id
     * @param available true if available
     */
    public static synchronized void setKeyAvailable(String keyId,
            boolean available) {
        log.trace("setKeyAvailable({}, {})", keyId, available);

        findKey(keyId).setAvailable(available);
    }

    /**
     * @param keyId the key id
     * @return true if key is available
     */
    public static synchronized boolean isKeyAvailable(String keyId) {
        log.trace("isKeyAvailable()");

        return findKey(keyId).isAvailable();
    }

    /**
     * Sets the key friendly name.
     * @param keyId the key id
     * @param friendlyName the friendly name
     */
    public static synchronized void setKeyFriendlyName(String keyId,
            String friendlyName) {
        log.trace("setKeyFriendlyName({}, {})", keyId, friendlyName);

        findKey(keyId).setFriendlyName(friendlyName);
    }

    /**
     * Sets the key usage.
     * @param keyId the key id
     * @param keyUsage the key usage
     */
    public static synchronized void setKeyUsage(String keyId,
            KeyUsageInfo keyUsage) {
        log.trace("setKeyUsage({}, {})", keyId, keyUsage);

        findKey(keyId).setUsage(keyUsage);
    }

    /**
     * Adds a key with id and base64 public key to a token.
     * @param tokenId the token id
     * @param keyId the key if
     * @param publicKeyBase64 the public key base64
     * @return the key info or throws exception if the token cannot be found
     */
    public static synchronized KeyInfo addKey(String tokenId, String keyId,
            String publicKeyBase64) {
        log.trace("addKey({}, {})", tokenId, keyId);

        Token token = findToken(tokenId);

        Key key = new Key(token, keyId);
        key.setPublicKey(publicKeyBase64);

        token.addKey(key);

        return key.toDTO();
    }

    /**
     * Removes a key with key id.
     * @param keyId the key id
     * @return true if key was removed
     */
    public static synchronized boolean removeKey(String keyId) {
        log.trace("removeKey({})", keyId);

        return forKey((t, k) -> k.getId().equals(keyId),
                (t, k) -> t.getKeys().remove(k)).orElse(false);
    }

    /**
     * Sets the public key for a key.
     * @param keyId the key id
     * @param publicKeyBase64 the public key base64
     */
    public static synchronized void setPublicKey(String keyId,
            String publicKeyBase64) {
        log.trace("setPublicKey({}, {})", keyId, publicKeyBase64);

        findKey(keyId).setPublicKey(publicKeyBase64);
    }

    /**
     * Adds a certificate to a key. Throws exception, if key cannot be found.
     * @param keyId the key id
     * @param certBytes the certificate bytes
     */
    public static synchronized void addCert(String keyId, byte[] certBytes) {
        log.trace("addCert({})", keyId);

        Key key = findKey(keyId);

        Cert cert = new Cert(SignerUtil.randomId());
        cert.setCertificate(certBytes);

        key.addCert(cert);
    }

    /**
     * Adds a certificate to a key. Throws exception, if key cannot be found.
     * @param keyId the key id
     * @param certInfo the certificate info
     */
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

    /**
     * Sets the certificate active status.
     * @param certId the certificate id
     * @param active true if active
     */
    public static synchronized void setCertActive(String certId,
            boolean active) {
        log.trace("setCertActive({}, {})", certId, active);

        findCert(certId).setActive(active);
    }

    /**
     * Sets the certificate status.
     * @param certId the certificate id
     * @param status the status
     */
    public static synchronized void setCertStatus(String certId,
            String status) {
        log.trace("setCertStatus({}, {})", certId, status);

        findCert(certId).setStatus(status);
    }

    /**
     * Removes certificate with given id.
     * @param certId the certificate id
     * @return true if certificate was removed
     */
    public static synchronized boolean removeCert(String certId) {
        log.trace("removeCert({})", certId);

        return forCert((k, c) -> c.getId().equals(certId),
                (k, c) -> k.getCerts().remove(c)).orElse(false);
    }

    /**
     * Adds a new certificate request to a key.
     * @param keyId the key id
     * @param memberId the member id
     * @param subjectName the sbject name
     * @param keyUsage the key usage
     * @return certificate id
     */
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
                log.warn("Certificate request (memberId: {}, "
                                + "subjectName: {}) already exists", memberId,
                        subjectName);
                return certRequest.getId();
            }
        }

        String certId = SignerUtil.randomId();
        key.addCertRequest(new CertRequest(certId, memberId, subjectName));

        log.info("Added new certificate request (memberId: {}, "
                + "subjectId: {}) under key {}",
                new Object[] {memberId, subjectName, keyId});

        return certId;
    }

    /**
     * Removes a certificate request with given id.
     * @param certReqId the certificate request id
     * @return key id from which the certificate request was removed
     */
    public static synchronized String removeCertRequest(String certReqId) {
        log.trace("removeCertRequest({})", certReqId);

        return forCertRequest((k, c) -> c.getId().equals(certReqId),
                (k, c) -> {
                    if (k.getUsage() == KeyUsageInfo.AUTHENTICATION) {
                        // Authentication keys can only have one certificate request
                        k.getCertRequests().clear();
                    } else {
                        if (!k.getCertRequests().remove(c)) {
                            return null;
                        }
                    }

                    return k.getId();
                }).orElse(null);
    }

    /**
     * Sets the token info for the token.
     * @param tokenId the token id
     * @param info the token info
     */
    public static synchronized void setTokenInfo(String tokenId,
            Map<String, String> info) {
        findToken(tokenId).setInfo(info);
    }

    /**
     * @param tokenId the token id
     * @return true if batch signing is enabled for a token
     */
    public static synchronized boolean isBatchSigningEnabled(String tokenId) {
        log.trace("isBatchSigningEnabled({})", tokenId);

        return findToken(tokenId).isBatchSigningEnabled();
    }

    // ------------------------------------------------------------------------

    private static <T> Optional<T> forToken(Function<Token, Boolean> tester,
            Function<Token, T> mapper) {
        for (Token token : currentTokens) {
            if (tester.apply(token)) {
                return Optional.ofNullable(mapper.apply(token));
            }
        }

        return Optional.empty();
    }

    private static <T> Optional<T> forKey(
            BiFunction<Token, Key, Boolean> tester,
            BiFunction<Token, Key, T> mapper) {
        for (Token token : currentTokens) {
            for (Key key : token.getKeys()) {
                if (tester.apply(token, key)) {
                    return Optional.ofNullable(mapper.apply(token, key));
                }
            }
        }

        return Optional.empty();
    }

    private static <T> Optional<T> forCert(
            BiFunction<Key, Cert, Boolean> tester,
            BiFunction<Key, Cert, T> mapper) {
        for (Token token : currentTokens) {
            for (Key key : token.getKeys()) {
                for (Cert cert : key.getCerts()) {
                    if (tester.apply(key, cert)) {
                        return Optional.ofNullable(mapper.apply(key, cert));
                    }
                }
            }
        }

        return Optional.empty();
    }

    private static <T> Optional<T> forCertRequest(
            BiFunction<Key, CertRequest, Boolean> tester,
            BiFunction<Key, CertRequest, T> mapper) {
        for (Token token : currentTokens) {
            for (Key key : token.getKeys()) {
                for (CertRequest certReq : key.getCertRequests()) {
                    if (tester.apply(key, certReq)) {
                        return Optional.ofNullable(mapper.apply(key, certReq));
                    }
                }
            }
        }

        return Optional.empty();
    }

    private static Token findToken(String tokenId) {
        log.trace("findToken({})", tokenId);

        return forToken(t -> t.getId().equals(tokenId), t -> t)
                .orElseThrow(() -> tokenNotFound(tokenId));
    }

    private static Key findKey(String keyId) {
        log.trace("findKey({})", keyId);

        return forKey((t, k) -> k.getId().equals(keyId), (t, k) -> k)
                .orElseThrow(() -> keyNotFound(keyId));
    }

    private static Cert findCert(String certId) {
        log.trace("findCert({})", certId);

        return forCert((k, c) -> c.getId().equals(certId), (k, c) -> c)
                .orElseThrow(() -> certWithIdNotFound(certId));
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
