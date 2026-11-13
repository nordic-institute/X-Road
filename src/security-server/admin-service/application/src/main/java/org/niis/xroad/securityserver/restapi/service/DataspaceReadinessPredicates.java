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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.CryptoUtils;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.securityserver.restapi.repository.ServerConfRepository;
import org.niis.xroad.serverconf.impl.entity.ServerConfEntity;
import org.niis.xroad.serverconf.model.Client;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Shared readiness predicates used by the DSP provisioning worker, the DSP status service, the catalog
 * invalidation notifier and the DS TLS renewal readiness gate. The callers key their logic on the same state;
 * a single implementation prevents them from silently diverging.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataspaceReadinessPredicates {

    private final GlobalConfProvider globalConfProvider;
    private final ServerConfRepository serverConfRepository;
    private final SignerRpcClient signerRpcClient;

    @Setter(AccessLevel.PACKAGE)
    private Clock clock = Clock.systemUTC();

    /**
     * Returns {@code true} if the SS has at least one certificate in REGISTERED state on an authentication key,
     * whether or not that certificate is usable for signing a request now.
     * Treats signer unavailability as "not registered".
     */
    public boolean hasRegisteredAuthCert() {
        try {
            return authKeys().flatMap(key -> key.getCerts().stream()).anyMatch(DataspaceReadinessPredicates::isRegistered);
        } catch (RuntimeException e) {
            log.debug("Data space: could not read signer state, treating auth cert as not registered", e);
            return false;
        }
    }

    /**
     * Returns the bytes of the usable authentication certificate that expires last, or empty when there is none.
     * A certificate is usable when it is in REGISTERED state on an available authentication key, it can be parsed
     * and it is valid at the current time.
     * Treats signer unavailability as "not registered".
     */
    public Optional<byte[]> registeredAuthCertBytes() {
        try {
            var now = clock.instant();
            return authKeys()
                    .filter(KeyInfo::isAvailable)
                    .flatMap(key -> key.getCerts().stream())
                    .filter(DataspaceReadinessPredicates::isRegistered)
                    .map(CertificateInfo::getCertificateBytes)
                    .flatMap(bytes -> validityOf(bytes, now).stream())
                    .max(Comparator.comparing(Usable::notAfter))
                    .map(Usable::bytes);
        } catch (RuntimeException e) {
            log.debug("Data space: could not read signer state, treating auth cert as not registered", e);
            return Optional.empty();
        }
    }

    private Stream<KeyInfo> authKeys() {
        return signerRpcClient.getTokens().stream()
                .flatMap(token -> token.getKeyInfo().stream())
                .filter(key -> KeyUsageInfo.AUTHENTICATION.equals(key.getUsage()));
    }

    private static boolean isRegistered(CertificateInfo cert) {
        return CertificateInfo.STATUS_REGISTERED.equals(cert.getStatus());
    }

    private static Optional<Usable> validityOf(byte[] certificateBytes, Instant now) {
        try {
            var certificate = CryptoUtils.readCertificate(certificateBytes);
            var notAfter = certificate.getNotAfter().toInstant();
            var valid = !now.isBefore(certificate.getNotBefore().toInstant()) && !now.isAfter(notAfter);
            return valid ? Optional.of(new Usable(certificateBytes, notAfter)) : Optional.empty();
        } catch (RuntimeException _) {
            return Optional.empty();
        }
    }

    private record Usable(byte[] bytes, Instant notAfter) {
    }

    /**
     * Returns {@code true} if the global configuration is valid now, that is, not expired.
     * Read on every call so the answer follows the next valid configuration download.
     */
    public boolean isGlobalConfUsable() {
        return globalConfProvider.isValid();
    }

    /**
     * Returns {@code true} if the global configuration is usable and lists at least one credential issuer
     * for this instance.
     * Read on every call so the answer follows globalconf additions and removals.
     * Treats any globalconf failure (no anchor yet, no shared parameters yet) as "not ready".
     */
    public boolean isDataspaceReady() {
        try {
            return isGlobalConfUsable()
                    && !globalConfProvider.getCredentialIssuerDids(globalConfProvider.getInstanceIdentifier()).isEmpty();
        } catch (RuntimeException e) {
            log.debug("Data space: readiness check failed, treating the instance as not ready: {}",
                    e.getMessage());
            return false;
        }
    }

    /**
     * Returns {@code true} if the management-request subsystem is registered as a client on this SS.
     * Loads server configuration from the repository inside the active transaction.
     * Treats globalconf or serverconf unavailability as "not registered".
     */
    @Transactional(readOnly = true)
    public boolean isManagementSubsystemRegistered() {
        try {
            ClientId managementService = globalConfProvider.getManagementRequestService();
            if (managementService == null) {
                return false;
            }
            ServerConfEntity serverConf = serverConfRepository.getServerConf();
            return serverConf.getClients().stream()
                    .anyMatch(client -> managementService.equals(client.getIdentifier())
                            && Client.STATUS_REGISTERED.equals(client.getClientStatus()));
        } catch (XrdRuntimeException e) {
            log.debug("Data space: could not determine management subsystem registration, treating as not registered", e);
            return false;
        }
    }
}
