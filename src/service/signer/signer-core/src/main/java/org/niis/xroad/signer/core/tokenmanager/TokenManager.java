/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package org.niis.xroad.signer.core.tokenmanager;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.crypto.identifier.SignMechanism;
import ee.ria.xroad.common.identifier.ClientId;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.niis.xroad.signer.api.dto.CertRequestInfo;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.api.dto.TokenInfoAndKeyId;
import org.niis.xroad.signer.core.model.Cert;
import org.niis.xroad.signer.core.model.CertRequest;
import org.niis.xroad.signer.core.model.Key;
import org.niis.xroad.signer.core.model.Token;
import org.niis.xroad.signer.core.tokenmanager.module.SoftwareModuleType;
import org.niis.xroad.signer.core.tokenmanager.token.TokenType;
import org.niis.xroad.signer.core.util.SignerUtil;
import org.niis.xroad.signer.core.util.TokenAndKey;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;
import org.niis.xroad.signer.protocol.dto.TokenStatusInfo;

import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static ee.ria.xroad.common.ErrorCodes.X_WRONG_CERT_USAGE;
import static org.niis.xroad.signer.core.util.ExceptionHelper.certWithHashNotFound;
import static org.niis.xroad.signer.core.util.ExceptionHelper.csrWithIdNotFound;
import static org.niis.xroad.signer.core.util.ExceptionHelper.keyNotFound;
import static org.niis.xroad.signer.core.util.ExceptionHelper.tokenNotFound;

/**
 * Manages the current state of tokens, their keys and certificates.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public final class TokenManager {
    private final TokenRegistry tokenRegistry;

    /**
     * @return list of tokens
     */
    public List<TokenInfo> listTokens() {
        return tokenRegistry.readAction(ctx -> ctx.getTokens().stream()
                .map(Token::toDTO)
                .toList());
    }

    /**
     * @param tokenId the token id
     * @return list of keys for a token
     */
    public List<KeyInfo> listKeys(String tokenId) {
        return findTokenInfo(tokenId).getKeyInfo();
    }

    /**
     * Creates a new token with specified type.
     *
     * @param tokenType the type
     * @return the new token
     */
    public TokenInfo createToken(TokenType tokenType) {
        Token token = new Token(tokenType.getModuleType(), tokenType.getId());
        token.setModuleId(tokenType.getModuleType());
        token.setReadOnly(tokenType.isReadOnly());
        token.setSerialNumber(tokenType.getSerialNumber());
        token.setLabel(tokenType.getLabel());
        token.setSlotIndex(tokenType.getSlotIndex());
        token.setFriendlyName(getDefaultFriendlyName(tokenType));
        token.setBatchSigningEnabled(tokenType.isBatchSigningEnabled());
        token.setAvailable(true);

        tokenRegistry.writeAction(ctx -> ctx.getTokens().add(token));

        return token.toDTO();
    }

    /**
     * @param tokenId the token id
     * @return the token info DTO for the token id or
     * throws exception if not found
     */
    public TokenInfo findTokenInfo(String tokenId) {
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
    public TokenInfo getTokenInfo(String tokenId) {
        log.trace("getTokenInfo({})", tokenId);

        return tokenRegistry.readAction(ctx -> ctx.getTokens().stream()
                .filter(t -> t.getId().equals(tokenId))
                .map(Token::toDTO)
                .findFirst()
                .orElse(null));
    }

    /**
     * @param keyId id of a key inside the token
     * @return the token info DTO for the token
     * @throws Exception if key was not found
     */
    public TokenInfo findTokenInfoForKeyId(String keyId) {
        log.trace("getTokenInfoForKeyId({})", keyId);

        return tokenRegistry.readAction(ctx -> {
            String tokenId = ctx.forKey(
                            (t, k) -> k.getId().equals(keyId),
                            (t, k) -> t.getId())
                    .orElseThrow(() -> keyNotFound(keyId));

            return ctx.getTokens().stream()
                    .filter(t -> t.getId().equals(tokenId))
                    .map(Token::toDTO)
                    .findFirst()
                    .orElseThrow(() -> tokenNotFound(tokenId));
        });
    }

    /**
     * @param keyId the key id
     * @return the token and key or throws exception if not found
     */
    public TokenAndKey findTokenAndKey(String keyId) {
        log.trace("findTokenAndKey({})", keyId);

        return tokenRegistry.readAction(ctx ->
                ctx.forKey(
                                (t, k) -> k.getId().equals(keyId),
                                (t, k) -> new TokenAndKey(t.getId(), k.toDTO()))
                        .orElseThrow(() -> keyNotFound(keyId)));
    }

    /**
     * @param certHash the certificate hash in HEX
     * @return the tokenInfo and key id, or throws exception if not found
     */
    public TokenInfoAndKeyId findTokenAndKeyIdForCertHash(String certHash) {
        log.trace("findTokenAndKeyIdForCertHash({})", certHash);

        return tokenRegistry.readAction(ctx -> {
            String keyId = ctx.forCert((k, c) -> certHash.equals(c.getSha256hash()), (k, c) -> k.getId())
                    .orElseThrow(() -> certWithHashNotFound(certHash));

            return ctx.forKey((t, k) -> k.getId().equals(keyId),
                            (t, k) -> new TokenInfoAndKeyId(t.toDTO(), keyId))
                    .orElseThrow(() -> keyNotFound(keyId));
        });
    }

    /**
     * @param certRequestId the certificate request id
     * @return the tokenInfo and key id, or throws exception if not found
     */
    public TokenInfoAndKeyId findTokenAndKeyIdForCertRequestId(String certRequestId) {
        log.trace("findTokenAndKeyIdForCertRequestId({})", certRequestId);

        return tokenRegistry.readAction(ctx -> {
            String keyId = ctx.forCertRequest((k, c) -> certRequestId.equals(c.getId()),
                            (k, c) -> k.getId())
                    .orElseThrow(() -> csrWithIdNotFound(certRequestId));

            return ctx.forKey((t, k) -> k.getId().equals(keyId),
                            (t, k) -> new TokenInfoAndKeyId(t.toDTO(), keyId))
                    .orElseThrow(() -> keyNotFound(keyId));
        });
    }

    /**
     * @param keyId the key id
     * @return the token id for the key id or throws exception if not found
     */
    public String findTokenIdForKeyId(String keyId) {
        log.trace("findTokenIdForKeyId({})", keyId);

        return tokenRegistry.readAction(ctx ->
                ctx.forKey(
                        (t, k) -> k.getId().equals(keyId),
                        (t, k) -> t.getId()).orElseThrow(() -> keyNotFound(keyId)));
    }

    /**
     * @return the software token id
     */
    public String getSoftwareTokenId() {
        return tokenRegistry.readAction(ctx ->
                ctx.forToken(t -> t.getType().equals(SoftwareModuleType.TYPE), Token::getId).orElse(null));
    }

    /**
     * @param keyId the key id
     * @return the key info for the key id or throws exception if not found
     */
    public KeyInfo findKeyInfo(String keyId) {
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
    public KeyInfo getKeyInfo(String keyId) {
        log.trace("getKeyInfo({})", keyId);
        return tokenRegistry.readAction(ctx ->
                ctx.forKey(
                        (t, k) -> k.getId().equals(keyId),
                        (t, k) -> k.toDTO()).orElse(null));
    }

    public Optional<SignMechanism> getKeySignMechanismInfo(String keyId) {
        log.trace("getKeySignMechanismInfo({})", keyId);
        return tokenRegistry.readAction(ctx ->
                ctx.forKey(
                        (t, k) -> k.getId().equals(keyId),
                        (t, k) -> k.getSignMechanismName()));
    }

    /**
     * @param clientId the client id
     * @return the list of keys for the given client id
     */
    public List<KeyInfo> getKeyInfo(ClientId clientId) {
        log.trace("getKeyInfo({})", clientId);

        List<KeyInfo> keyInfo = new ArrayList<>();

        return tokenRegistry.readAction(ctx -> {
            for (Token token : ctx.getTokens()) {
                if (token.isInActive()) {
                    // Ignore inactive (not usable) tokens
                    continue;
                }

                for (Key key : token.getKeys()) {
                    if (!key.isValidForSigning()) {
                        // Ignore authentication keys
                        continue;
                    }

                    for (Cert cert : key.getCerts()) {
                        if (cert.isInvalid()) {
                            // Ignore inactive and invalid certificates
                            continue;
                        }

                        if (cert.toDTO().belongsToMember(clientId)) {
                            log.debug("Found key '{}' for client '{}'",
                                    key.getId(), cert.getMemberId());
                            keyInfo.add(key.toDTO());
                        }
                    }
                }
            }

            return keyInfo;
        });
    }

    /**
     * @param certId the certificate id
     * @return the certificate info for the certificate id or null if not found
     */
    public CertificateInfo getCertificateInfo(String certId) {
        log.trace("getCertificateInfo({})", certId);

        return tokenRegistry.readAction(ctx ->
                ctx.forCert((k, c) -> c.getId().equals(certId), (k, c) -> c.toDTO()).orElse(null));
    }

    /**
     * @param certHash the certificate hash in HEX
     * @return the certificate info for the certificate hash or null
     */
    public CertificateInfo getCertificateInfoForCertHash(String certHash) {
        log.trace("getCertificateInfoForCertHash({})", certHash);

        return tokenRegistry.readAction(ctx ->
                ctx.forCert((k, c) -> certHash.equals(c.getSha256hash()), (k, c) -> c.toDTO()).orElse(null));
    }

    /**
     * @param certSha1Hash the certificate SHA-1 hash in HEX
     * @return the certificate for the certificate hash or null
     */
    public X509Certificate getCertificateForCerHash(String certSha1Hash) {
        log.trace("getCertificateForCertHash({})", certSha1Hash);

        return tokenRegistry.readAction(ctx ->
                ctx.forCert((k, c) -> certSha1Hash.equals(c.getSha1hash()), (k, c) -> c.getCertificate()).orElse(null));
    }

    /**
     * @return all certificates
     */
    public List<CertificateInfo> getAllCerts() {
        log.trace("getAllCerts()");

        return tokenRegistry.readAction(ctx ->
                ctx.getTokens().stream()
                        .flatMap(t -> t.getKeys().stream())
                        .flatMap(k -> k.getCerts().stream())
                        .map(Cert::toDTO)
                        .toList());
    }

    /**
     * Sets the OCSP response for the certificate.
     *
     * @param certSha1Hash the certificate SHA-1 hash in HEX
     * @param response     the OCSP response
     */
    public void setOcspResponse(String certSha1Hash, OCSPResp response) {
        log.trace("setOcspResponse({})", certSha1Hash);

        tokenRegistry.writeRun(ctx ->
                ctx.forCert((k, c) -> certSha1Hash.equals(c.getSha1hash()), (k, c) -> {
                    c.setOcspResponse(response);
                    return null;
                }));
    }

    /**
     * @param keyId    the key id
     * @param memberId the member id
     * @return the certificate request info or null if not found
     */
    public CertRequestInfo getCertRequestInfo(String keyId, ClientId memberId) {
        log.trace("getCertRequestInfo({}, {})", keyId, memberId);

        return tokenRegistry.readAction(ctx -> {
            Key key = ctx.findKey(keyId);
            return key.getCertRequests().stream()
                    .filter(c -> key.getUsage() == KeyUsageInfo.AUTHENTICATION
                            || memberId.equals(c.getMemberId()))
                    .map(CertRequest::toDTO)
                    .findFirst()
                    .orElse(null);
        });
    }

    /**
     * @param certReqId cert request id
     * @return the certificate request info or null if not found
     */
    public CertRequestInfo getCertRequestInfo(String certReqId) {
        log.trace("getCertRequestInfo({})", certReqId);

        return tokenRegistry.readAction(ctx ->
                ctx.forCertRequest(
                                (k, c) -> certReqId.equals(c.getId()),
                                (k, c) -> c.toDTO())
                        .orElse(null));
    }

    /**
     * @param certHash the certificate hash in HEX
     * @return key info for the certificate hash
     */
    public KeyInfo getKeyInfoForCertHash(String certHash) {
        log.trace("getKeyInfoForCertHash({})", certHash);

        return tokenRegistry.readAction(ctx ->
                ctx.forCert((k, c) -> certHash.equals(c.getSha256hash()), (k, c) -> k.toDTO()).orElse(null));
    }

    /**
     * @param certId the certificate id
     * @return key info for certificate id
     */
    public KeyInfo getKeyInfoForCertId(String certId) {
        log.trace("getKeyInfoForCertId({})", certId);

        return tokenRegistry.readAction(ctx ->
                ctx.forCert((k, c) -> c.getId().equals(certId), (k, c) -> k.toDTO()).orElse(null));
    }


    /**
     * @param tokenId the token id
     * @return true if token is available
     */
    public boolean isTokenAvailable(String tokenId) {
        log.trace("isTokenAvailable({})", tokenId);

        return tokenRegistry.readAction(ctx ->
                ctx.findToken(tokenId).isAvailable());
    }

    /**
     * @param tokenId the token id
     * @return true if token is active (logged in)
     */
    public boolean isTokenActive(String tokenId) {
        log.trace("isTokenActive({})", tokenId);

        return tokenRegistry.readAction(ctx ->
                ctx.findToken(tokenId).isActive());
    }

    /**
     * Sets the token available.
     *
     * @param tokenType the token type
     * @param available availability flag
     */
    public void setTokenAvailable(TokenType tokenType,
                                  boolean available) {
        String tokenId = tokenType.getId();

        log.trace("setTokenAvailable({}, {})", tokenId, available);
        tokenRegistry.writeRun(ctx -> {
            Token token = ctx.findToken(tokenId);
            token.setAvailable(available);
            token.setModuleId(tokenType.getModuleType());
        });
    }

    /**
     * Sets the token available.
     *
     * @param tokenId   the token id
     * @param available availability flag
     */
    public void setTokenAvailable(String tokenId, boolean available) {
        log.trace("setTokenAvailable({}, {})", tokenId, available);

        tokenRegistry.writeRun(ctx ->
                ctx.findToken(tokenId).setAvailable(available));
    }

    /**
     * Sets the token active (logged in) or not
     *
     * @param tokenId the token id
     * @param active  active flag
     */
    public void setTokenActive(String tokenId,
                               boolean active) {
        log.trace("setTokenActive({}, {})", tokenId, active);

        tokenRegistry.writeRun(ctx -> ctx.findToken(tokenId).setActive(active));
    }

    /**
     * Sets the token friendly name.
     *
     * @param tokenId      token id
     * @param friendlyName the friendly name
     */
    public void setTokenFriendlyName(String tokenId,
                                     String friendlyName) {
        log.trace("setTokenFriendlyName({}, {})", tokenId, friendlyName);

        tokenRegistry.writeRun(ctx ->
                ctx.findToken(tokenId).setFriendlyName(friendlyName));
    }

    /**
     * Sets the token status info
     *
     * @param tokenId the token id
     * @param status  the status
     */
    public void setTokenStatus(String tokenId,
                               TokenStatusInfo status) {
        log.trace("setTokenStatus({}, {})", tokenId, status);

        tokenRegistry.writeRun(ctx ->
                ctx.findToken(tokenId).setStatus(status));
    }

    /**
     * Sets the key availability.
     *
     * @param keyId     the key id
     * @param available true if available
     */
    public void setKeyAvailable(String keyId,
                                boolean available) {
        log.trace("setKeyAvailable({}, {})", keyId, available);

        tokenRegistry.writeRun(ctx ->
                ctx.findKey(keyId).setAvailable(available));
    }

    /**
     * @param keyId the key id
     * @return true if key is available
     */
    public boolean isKeyAvailable(String keyId) {
        log.trace("isKeyAvailable()");

        return tokenRegistry.readAction(ctx ->
                ctx.findKey(keyId).isAvailable());
    }

    /**
     * Sets the key friendly name.
     *
     * @param keyId        the key id
     * @param friendlyName the friendly name
     */
    public void setKeyFriendlyName(String keyId,
                                   String friendlyName) {
        log.trace("setKeyFriendlyName({}, {})", keyId, friendlyName);
        tokenRegistry.writeRun(ctx ->
                ctx.findKey(keyId).setFriendlyName(friendlyName));
    }

    /**
     * Delete token.
     *
     * @param tokenId the token id
     */
    public void deleteToken(String tokenId) {
        log.trace("deleteToken({})", tokenId);

        tokenRegistry.writeRun(ctx ->
                ctx.getTokens().remove(ctx.findToken(tokenId)));
    }

    /**
     * Sets the key label.
     *
     * @param keyId the key id
     * @param label the label
     */
    public void setKeyLabel(String keyId, String label) {
        log.trace("setKeyLabel({}, {})", keyId, label);

        tokenRegistry.writeRun(ctx ->
                ctx.findKey(keyId).setLabel(label));
    }

    /**
     * Sets the key usage.
     *
     * @param keyId    the key id
     * @param keyUsage the key usage
     */
    public void setKeyUsage(String keyId,
                            KeyUsageInfo keyUsage) {
        log.trace("setKeyUsage({}, {})", keyId, keyUsage);
        tokenRegistry.writeRun(ctx ->
                ctx.findKey(keyId).setUsage(keyUsage));
    }

    /**
     * Adds a key with id and base64 public key to a token.
     *
     * @param tokenId         the token id
     * @param keyId           the key if
     * @param publicKeyBase64 the public key base64
     * @return the key info or throws exception if the token cannot be found
     */
    public KeyInfo addKey(String tokenId, String keyId, String publicKeyBase64, SignMechanism signMechanism) {
        log.trace("addKey({}, {})", tokenId, keyId);

        return tokenRegistry.writeAction(ctx -> {
            Token token = ctx.findToken(tokenId);

            Key key = new Key(token, keyId, signMechanism);
            key.setPublicKey(publicKeyBase64);

            token.addKey(key);

            return key.toDTO();
        });
    }

    /**
     * Removes a key with key id.
     *
     * @param keyId the key id
     * @return true if key was removed
     */
    public boolean removeKey(String keyId) {
        log.trace("removeKey({})", keyId);

        return tokenRegistry.writeAction(ctx ->
                ctx.forKey(
                                (t, k) -> k.getId().equals(keyId),
                                (t, k) -> t.getKeys().remove(k))
                        .orElse(false)
        );
    }

    /**
     * Sets the public key for a key.
     *
     * @param keyId           the key id
     * @param publicKeyBase64 the public key base64
     */
    public void setPublicKey(String keyId,
                             String publicKeyBase64) {
        log.trace("setPublicKey({}, {})", keyId, publicKeyBase64);
        tokenRegistry.writeRun(ctx ->
                ctx.findKey(keyId).setPublicKey(publicKeyBase64)
        );
    }

    /**
     * Adds a certificate to a key. Throws exception, if key cannot be found.
     *
     * @param keyId     the key id
     * @param certBytes the certificate bytes
     */
    public void addCert(String keyId, byte[] certBytes) {
        log.trace("addCert({})", keyId);

        tokenRegistry.writeRun(ctx -> {
            Key key = ctx.findKey(keyId);

            Cert cert = new Cert(SignerUtil.randomId());
            cert.setCertificate(certBytes);

            key.addCert(cert);
        });
    }

    /**
     * Adds a certificate to a key. Throws exception, if key cannot be found.
     */
    public void addCert(String keyId, ClientId.Conf memberId, boolean savedToConfiguration,
                        String initialStatus, String id, byte[] certificate) {
        log.trace("addCert({})", keyId);

        tokenRegistry.writeRun(ctx -> {
            Key key = ctx.findKey(keyId);

            Cert cert = new Cert(id);
            cert.setCertificate(certificate);
            cert.setMemberId(memberId);
            cert.setSavedToConfiguration(savedToConfiguration);
            cert.setStatus(initialStatus);

            key.addCert(cert);
        });
    }

    /**
     * Sets the certificate active status.
     *
     * @param certId the certificate id
     * @param active true if active
     */
    public void setCertActive(String certId,
                              boolean active) {
        log.trace("setCertActive({}, {})", certId, active);

        tokenRegistry.writeRun(ctx ->
                ctx.findCert(certId).setActive(active)
        );
    }

    /**
     * Sets the certificate status.
     *
     * @param certId the certificate id
     * @param status the status
     */
    public void setCertStatus(String certId,
                              String status) {
        log.trace("setCertStatus({}, {})", certId, status);

        tokenRegistry.writeRun(ctx ->
                ctx.findCert(certId).setStatus(status)
        );
    }

    /**
     * Sets the certificate hash for the newer certificate.
     *
     * @param certId the certificate id
     * @param hash   the hash of the newer certificate
     */
    public void setRenewedCertHash(String certId,
                                   String hash) {
        log.trace("setRenewedCertHash({}, {})", certId, hash);

        tokenRegistry.writeRun(ctx ->
                ctx.findCert(certId).setRenewedCertHash(hash)
        );
    }

    /**
     * Sets the error message that was thrown during the automatic certificate renewal process.
     *
     * @param certId       the certificate id
     * @param errorMessage error message of the thrown error
     */
    public void setRenewalError(String certId,
                                String errorMessage) {
        log.trace("setRenewalError({}, {})", certId, errorMessage);

        tokenRegistry.writeRun(ctx ->
                ctx.findCert(certId).setRenewalError(errorMessage)
        );
    }

    /**
     * Sets the error message that was thrown during the automatic certificate renewal process.
     *
     * @param certId       the certificate id
     * @param errorMessage error message of the thrown error
     */
    public void setOcspVerifyBeforeActivationError(String certId,
                                                   String errorMessage) {
        log.trace("setOcspVerifyError({}, {})", certId, errorMessage);

        tokenRegistry.writeRun(ctx ->
                ctx.findCert(certId).setOcspVerifyBeforeActivationError(errorMessage)
        );
    }

    /**
     * Sets the next planned renewal time for the certificate.
     *
     * @param certId          the certificate id
     * @param nextRenewalTime next planned renewal time
     */
    public void setNextPlannedRenewal(String certId,
                                      Instant nextRenewalTime) {
        log.trace("setNextPlannedRenewal({}, {})", certId, nextRenewalTime);

        tokenRegistry.writeRun(ctx ->
                ctx.findCert(certId).setNextAutomaticRenewalTime(nextRenewalTime));

    }

    /**
     * Removes certificate with given id.
     *
     * @param certId the certificate id
     * @return true if certificate was removed
     */
    public boolean removeCert(String certId) {
        log.trace("removeCert({})", certId);

        return tokenRegistry.writeAction(ctx ->
                ctx.forCert(
                                (k, c) -> c.getId().equals(certId),
                                (k, c) -> k.getCerts().remove(c))
                        .orElse(false));
    }

    /**
     * Adds a new certificate request to a key.
     *
     * @param keyId       the key id
     * @param memberId    the member id
     * @param subjectName the sbject name
     * @param keyUsage    the key usage
     * @return certificate id
     */
    public String addCertRequest(String keyId,
                                 ClientId.Conf memberId,
                                 String subjectName,
                                 String subjectAltName,
                                 KeyUsageInfo keyUsage,
                                 String certificateProfile) {
        log.trace("addCertRequest({}, {})", keyId, memberId);
        return tokenRegistry.writeAction(ctx -> {
            Key key = ctx.findKey(keyId);

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
            key.addCertRequest(new CertRequest(certId, memberId, subjectName, subjectAltName, certificateProfile));

            log.info("Added new certificate request (memberId: {}, subjectId: {}) under key {}", memberId, subjectName, keyId);

            return certId;
        });
    }

    /**
     * Removes a certificate request with given id.
     *
     * @param certReqId the certificate request id
     * @return key id from which the certificate request was removed
     */
    public String removeCertRequest(String certReqId) {
        log.trace("removeCertRequest({})", certReqId);

        return tokenRegistry.writeAction(ctx -> ctx.forCertRequest(
                (k, c) -> c.getId().equals(certReqId),
                (k, c) -> {
                    if (!k.getCertRequests().remove(c)) {
                        return null;
                    }

                    return k.getId();
                }).orElse(null));
    }

    /**
     * Sets the token info for the token.
     *
     * @param tokenId the token id
     * @param info    the token info
     */
    public void setTokenInfo(String tokenId, Map<String, String> info) {
        tokenRegistry.writeRun(ctx ->
                ctx.findToken(tokenId).setInfo(info));
    }

    /**
     * @param tokenId the token id
     * @return true if batch signing is enabled for a token
     */
    public boolean isBatchSigningEnabled(String tokenId) {
        log.trace("isBatchSigningEnabled({})", tokenId);

        return tokenRegistry.readAction(ctx -> ctx.findToken(tokenId).isBatchSigningEnabled());
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
