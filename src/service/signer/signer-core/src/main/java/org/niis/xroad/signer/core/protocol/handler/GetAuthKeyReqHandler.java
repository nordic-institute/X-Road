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
package org.niis.xroad.signer.core.protocol.handler;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.util.CertUtils;
import ee.ria.xroad.common.util.CryptoUtils;

import com.google.protobuf.ByteString;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.niis.xroad.common.core.annotation.ArchUnitSuppressed;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.rpc.mapper.SecurityServerIdMapper;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifier;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifierOptions;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.core.passwordstore.PasswordStore;
import org.niis.xroad.signer.core.protocol.AbstractRpcHandler;
import org.niis.xroad.signer.core.tokenmanager.TokenLookup;
import org.niis.xroad.signer.core.tokenmanager.TokenPinManager;
import org.niis.xroad.signer.core.tokenmanager.module.SoftwareModuleType;
import org.niis.xroad.signer.core.tokenmanager.token.SoftwareTokenDefinition;
import org.niis.xroad.signer.proto.AuthKeyProto;
import org.niis.xroad.signer.proto.GetAuthKeyReq;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import static ee.ria.xroad.common.ErrorCodes.X_KEY_NOT_FOUND;
import static java.util.Optional.ofNullable;
import static org.niis.xroad.signer.core.util.ExceptionHelper.tokenNotActive;
import static org.niis.xroad.signer.core.util.ExceptionHelper.tokenNotInitialized;

/**
 * Handles authentication key retrieval requests.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
@ArchUnitSuppressed("NoVanillaExceptions") //TODO XRDDEV-2962 review and refactor if needed
public class GetAuthKeyReqHandler extends AbstractRpcHandler<GetAuthKeyReq, AuthKeyProto> {
    private final GlobalConfProvider globalConfProvider;
    private final TokenLookup tokenLookup;
    private final TokenPinManager tokenPinManager;

    @Override
    @SuppressWarnings({"squid:S3776", "checkstyle:SneakyThrowsCheck"})//TODO XRDDEV-2390 will be refactored in the future
    @SneakyThrows
    protected AuthKeyProto handle(GetAuthKeyReq request) {
        var securityServer = SecurityServerIdMapper.fromDto(request.getSecurityServer());
        log.trace("Selecting authentication key for security server {}", securityServer);

        validateToken();

        for (TokenInfo tokenInfo : tokenLookup.listTokens()) {
            if (!SoftwareModuleType.TYPE.equals(tokenInfo.getType())) {
                log.trace("Ignoring {} module", tokenInfo.getType());
                continue;
            }

            for (KeyInfo keyInfo : tokenInfo.getKeyInfo()) {
                if (keyInfo.isForSigning()) {
                    log.trace("Ignoring {} key {}", keyInfo.getUsage(),
                            keyInfo.getId());
                    continue;
                }

                if (!keyInfo.isAvailable()) {
                    log.trace("Ignoring unavailable key {}", keyInfo.getId());
                    continue;
                }

                for (CertificateInfo certInfo : keyInfo.getCerts()) {
                    if (authCertValid(certInfo, securityServer)) {
                        log.trace("Found suitable authentication key {}", keyInfo.getId());
                        return resolveResponse(keyInfo, certInfo);
                    }
                }
            }
        }

        throw CodedException.tr(X_KEY_NOT_FOUND,
                "auth_key_not_found_for_server",
                "Could not find active authentication key for security server '%s'", securityServer);
    }

    private AuthKeyProto resolveResponse(KeyInfo keyInfo, CertificateInfo certInfo) throws Exception {
        final char[] password = PasswordStore.getPassword(SoftwareTokenDefinition.ID);

        final var builder = AuthKeyProto.newBuilder()
                .setAlias(keyInfo.getId())
                .setKeyStore(getKeyStore(keyInfo.getId()))
                .setCert(certInfo.asMessage());

        ofNullable(password).ifPresent(passwd -> builder.setPassword(new String(passwd)));
        return builder.build();
    }

    private ByteString getKeyStore(String keyId) {
        log.trace("Loading authentication key from database");
        return tokenLookup.getSoftwareTokenKeyStore(keyId)
                .map(ByteString::copyFrom)
                .orElseThrow(() -> XrdRuntimeException.systemInternalError("Key not found in key store: " + keyId));
    }

    private boolean authCertValid(CertificateInfo certInfo,
                                  SecurityServerId securityServer) throws Exception {
        X509Certificate cert = CryptoUtils.readCertificate(certInfo.getCertificateBytes());

        if (!certInfo.isActive()) {
            log.trace("Ignoring inactive authentication certificate {}",
                    CertUtils.identify(cert));

            return false;
        }

        if (!isRegistered(certInfo.getStatus())) {
            log.trace("Ignoring non-registered ({}) authentication certificate"
                    + " {}", certInfo.getStatus(), CertUtils.identify(cert));

            return false;
        }

        SecurityServerId serverIdFromConf = globalConfProvider.getServerId(cert);
        try {
            cert.checkValidity();

            if (securityServer.equals(serverIdFromConf)) {
                verifyOcspResponse(securityServer.getXRoadInstance(), cert,
                        certInfo.getOcspBytes(), new OcspVerifierOptions(
                                globalConfProvider.getGlobalConfExtensions()
                                        .shouldVerifyOcspNextUpdate()));

                return true;
            }
        } catch (Exception e) {
            log.warn("Ignoring authentication certificate '{}' because: ",
                    cert.getSubjectX500Principal().getName(), e);

            return false;
        }

        log.trace("Ignoring authentication certificate {} because it does "
                        + "not belong to security server {} "
                        + "(server id from global conf: {})", CertUtils.identify(cert),
                securityServer, serverIdFromConf);

        return false;
    }

    private void verifyOcspResponse(String instanceIdentifier,
                                    X509Certificate subject, byte[] ocspBytes, OcspVerifierOptions verifierOptions) throws Exception {
        if (ocspBytes == null) {
            throw new CertificateException("OCSP response not found");
        }

        OCSPResp ocsp = new OCSPResp(ocspBytes);
        X509Certificate issuer =
                globalConfProvider.getCaCert(instanceIdentifier, subject);
        OcspVerifier verifier =
                new OcspVerifier(globalConfProvider, verifierOptions);
        verifier.verifyValidityAndStatus(ocsp, subject, issuer);
    }

    private void validateToken() throws CodedException {
        if (!tokenPinManager.tokenHasPin(SoftwareTokenDefinition.ID)) {
            throw tokenNotInitialized(SoftwareTokenDefinition.ID);
        }

        if (!tokenLookup.isTokenActive(SoftwareTokenDefinition.ID)) {
            throw tokenNotActive(SoftwareTokenDefinition.ID);
        }
    }

    private static boolean isRegistered(String status) {
        return status != null && status.startsWith(CertificateInfo.STATUS_REGISTERED);
    }
}
