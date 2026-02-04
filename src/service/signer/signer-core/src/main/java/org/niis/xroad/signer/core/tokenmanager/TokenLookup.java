/*
 * The MIT License
 *
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

import ee.ria.xroad.common.crypto.identifier.SignMechanism;
import ee.ria.xroad.common.identifier.ClientId;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.signer.api.dto.CertRequestInfo;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.api.dto.TokenInfoAndKeyId;
import org.niis.xroad.signer.core.mapper.CertRequestInfoProtoMapper;
import org.niis.xroad.signer.core.mapper.CertificateInfoProtoMapper;
import org.niis.xroad.signer.core.mapper.KeyInfoProtoMapper;
import org.niis.xroad.signer.core.mapper.TokenInfoProtoMapper;
import org.niis.xroad.signer.core.model.RuntimeKey;
import org.niis.xroad.signer.core.model.RuntimeKeyImpl;
import org.niis.xroad.signer.core.model.RuntimeTokenImpl;
import org.niis.xroad.signer.core.tokenmanager.module.SoftwareModuleType;
import org.niis.xroad.signer.core.tokenmanager.token.TokenDefinition;
import org.niis.xroad.signer.core.util.TokenAndKey;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;

import java.util.List;
import java.util.Optional;

import static org.niis.xroad.signer.core.util.ExceptionHelper.certWithHashNotFound;
import static org.niis.xroad.signer.core.util.ExceptionHelper.csrWithIdNotFound;
import static org.niis.xroad.signer.core.util.ExceptionHelper.keyNotFound;
import static org.niis.xroad.signer.core.util.ExceptionHelper.tokenNotFound;

/**
 * Queries token, certificate and key information from the registry.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class TokenLookup {
    private final TokenRegistry tokenRegistry;
    private final TokenInfoProtoMapper tokenInfoProtoMapper;
    private final KeyInfoProtoMapper keyInfoProtoMapper;
    private final CertificateInfoProtoMapper certificateInfoProtoMapper;
    private final CertRequestInfoProtoMapper certRequestInfoProtoMapper;

    /**
     * @return list of tokens
     */
    public List<TokenInfo> listTokens() {
        return tokenRegistry.readAction(ctx -> ctx.getTokens().stream()
                .map(tokenInfoProtoMapper::toTargetDTO)
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
                .filter(t -> t.externalId().equals(tokenId))
                .map(tokenInfoProtoMapper::toTargetDTO)
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
                            (t, k) -> k.externalId().equals(keyId),
                            (t, k) -> t.externalId())
                    .orElseThrow(() -> keyNotFound(keyId));

            return ctx.getTokens().stream()
                    .filter(t -> t.externalId().equals(tokenId))
                    .map(tokenInfoProtoMapper::toTargetDTO)
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
                                (t, k) -> k.externalId().equals(keyId),
                                (t, k) -> new TokenAndKey(t.externalId(), keyInfoProtoMapper.toTargetDTO(k)))
                        .orElseThrow(() -> keyNotFound(keyId)));
    }

    /**
     * @param certHash the certificate hash in HEX
     * @return the tokenInfo and key id, or throws exception if not found
     */
    public TokenInfoAndKeyId findTokenAndKeyIdForCertHash(String certHash) {
        log.trace("findTokenAndKeyIdForCertHash({})", certHash);

        return tokenRegistry.readAction(ctx -> {
            String keyId = ctx.forCert((k, c) -> certHash.equals(c.sha256hash()), (k, c) -> k.externalId())
                    .orElseThrow(() -> certWithHashNotFound(certHash));

            return ctx.forKey((t, k) -> k.externalId().equals(keyId),
                            (t, k) -> new TokenInfoAndKeyId(tokenInfoProtoMapper.toTargetDTO(t), keyId))
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
            String keyId = ctx.forCertRequest((k, c) -> certRequestId.equals(c.externalId()),
                            (k, c) -> k.externalId())
                    .orElseThrow(() -> csrWithIdNotFound(certRequestId));

            return ctx.forKey((t, k) -> k.externalId().equals(keyId),
                            (t, k) -> new TokenInfoAndKeyId(tokenInfoProtoMapper.toTargetDTO(t), keyId))
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
                        (t, k) -> k.externalId().equals(keyId),
                        (t, k) -> t.externalId()).orElseThrow(() -> keyNotFound(keyId)));
    }

    /**
     * @return the software token id
     */
    public String getSoftwareTokenId() {
        return tokenRegistry.readAction(ctx ->
                ctx.forToken(t -> t.type().equals(SoftwareModuleType.TYPE), RuntimeTokenImpl::externalId).orElse(null));
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
                        (t, k) -> k.externalId().equals(keyId),
                        (t, k) -> keyInfoProtoMapper.toTargetDTO(k)).orElse(null));
    }

    public Optional<SignMechanism> getKeySignMechanismInfo(String keyId) {
        log.trace("getKeySignMechanismInfo({})", keyId);
        return tokenRegistry.readAction(ctx ->
                ctx.forKey(
                        (t, k) -> k.externalId().equals(keyId),
                        (t, k) -> k.signMechanismName()));
    }


    /**
     * @param clientId the client id
     * @return the list of keys for the given client id
     */
    public List<KeyInfo> getKeyInfo(ClientId clientId) {
        log.trace("getKeyInfo({})", clientId);

        return tokenRegistry.readAction(ctx ->
                ctx.getTokens().stream()
                        .filter(token -> !token.isInactive()) // Only active tokens
                        .flatMap(token -> token.keys().stream())
                        .filter(RuntimeKey::isValidForSigning) // Only signing keys
                        .filter(key -> key.certs().stream()
                                .anyMatch(cert -> !cert.isInvalid()
                                        && certificateInfoProtoMapper.toTargetDTO(cert).belongsToMember(clientId)))
                        .map(keyInfoProtoMapper::toTargetDTO)
                        .toList()
        );
    }

    /**
     * @param certId the certificate id
     * @return the certificate info for the certificate id or null if not found
     */
    public CertificateInfo getCertificateInfo(String certId) {
        log.trace("getCertificateInfo({})", certId);

        return tokenRegistry.readAction(ctx ->
                ctx.forCert((k, c) -> c.externalId().equals(certId), (k, c) -> certificateInfoProtoMapper.toTargetDTO(c)).orElse(null));
    }

    /**
     * @param certHash the certificate hash in HEX
     * @return the certificate info for the certificate hash or null
     */
    public CertificateInfo getCertificateInfoForCertHash(String certHash) {
        log.trace("getCertificateInfoForCertHash({})", certHash);

        return tokenRegistry.readAction(ctx ->
                ctx.forCert((k, c) -> certHash.equals(c.sha256hash()), (k, c) -> certificateInfoProtoMapper.toTargetDTO(c)).orElse(null));
    }

    /**
     * @return all certificates
     */
    public List<CertificateInfo> getAllCerts() {
        log.trace("getAllCerts()");

        return tokenRegistry.readAction(ctx ->
                ctx.getTokens().stream()
                        .flatMap(t -> t.keys().stream())
                        .flatMap(k -> k.certs().stream())
                        .map(certificateInfoProtoMapper::toTargetDTO)
                        .toList());
    }


    /**
     * @param keyId    the key id
     * @param memberId the member id
     * @return the certificate request info or null if not found
     */
    public CertRequestInfo getCertRequestInfo(String keyId, ClientId memberId) {
        log.trace("getCertRequestInfo({}, {})", keyId, memberId);

        return tokenRegistry.readAction(ctx -> {
            RuntimeKeyImpl key = ctx.findKey(keyId);
            return key.certRequests().stream()
                    .filter(c -> key.usage() == KeyUsageInfo.AUTHENTICATION
                            || memberId.equals(c.memberId()))
                    .map(certRequestInfoProtoMapper::toTargetDTO)
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
                                (k, c) -> certReqId.equals(c.externalId()),
                                (k, c) -> certRequestInfoProtoMapper.toTargetDTO(c))
                        .orElse(null));
    }

    /**
     * @param certHash the certificate hash in HEX
     * @return key info for the certificate hash
     */
    public KeyInfo getKeyInfoForCertHash(String certHash) {
        log.trace("getKeyInfoForCertHash({})", certHash);

        return tokenRegistry.readAction(ctx ->
                ctx.forCert((k, c) -> certHash.equals(c.sha256hash()), (k, c) -> keyInfoProtoMapper.toTargetDTO(k)).orElse(null));
    }

    /**
     * @param certId the certificate id
     * @return key info for certificate id
     */
    public KeyInfo getKeyInfoForCertId(String certId) {
        log.trace("getKeyInfoForCertId({})", certId);

        return tokenRegistry.readAction(ctx ->
                ctx.forCert((k, c) -> c.externalId().equals(certId), (k, c) -> keyInfoProtoMapper.toTargetDTO(k)).orElse(null));
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
     * @param keyId the key id
     * @return true if key is available
     */
    public boolean isKeyAvailable(String keyId) {
        log.trace("isKeyAvailable()");

        return tokenRegistry.readAction(ctx ->
                ctx.findKey(keyId).isAvailable());
    }

    public Optional<byte[]> getSoftwareTokenKeyStore(String keyId) {
        return tokenRegistry.readAction(ctx -> ctx.findKey(keyId).softwareKeyStore());
    }

    /**
     * @param tokenId the token id
     * @return true if batch signing is enabled for a token
     */
    public boolean isBatchSigningEnabled(String tokenId) {
        log.trace("isBatchSigningEnabled({})", tokenId);

        return tokenRegistry.readAction(ctx -> ctx.findToken(tokenId).getTokenDefinition()
                .map(TokenDefinition::batchSigningEnabled).orElse(true));

    }
}
