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

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.identifier.ClientId;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.signer.api.exception.SignerException;
import org.niis.xroad.signer.core.model.CertRequestData;
import org.niis.xroad.signer.core.model.RuntimeCert;
import org.niis.xroad.signer.core.model.RuntimeKey;
import org.niis.xroad.signer.core.model.RuntimeKeyImpl;
import org.niis.xroad.signer.core.service.TokenKeyCertRequestWriteService;
import org.niis.xroad.signer.core.service.TokenKeyCertWriteService;
import org.niis.xroad.signer.core.service.TokenKeyWriteService;
import org.niis.xroad.signer.core.util.SignerUtil;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;

import java.time.Instant;
import java.util.Optional;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_WRONG_CERT_USAGE;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class CertManager {
    private final TokenRegistry tokenRegistry;
    private final TokenKeyCertWriteService tokenKeyCertWriteService;
    private final TokenKeyWriteService tokenKeyWriteService;
    private final TokenKeyCertRequestWriteService tokenKeyCertRequestWriteService;

    /**
     * Adds a certificate to a key. Throws exception, if key cannot be found.
     */
    public void addCert(String keyId, ClientId.Conf memberId,
                        String initialStatus, String id, byte[] certificate) {
        log.trace("addCert({})", keyId);

        tokenRegistry.writeRun(ctx -> {
            try {
                var key = ctx.findKey(keyId);
                tokenKeyCertWriteService.save(key.id(), id, memberId, initialStatus, certificate);
            } catch (CodedException signerException) {
                throw signerException;
            } catch (Exception e) {
                throw new SignerException(X_INTERNAL_ERROR, "Failed to add certificate to key " + keyId, e);
            } finally {
                ctx.invalidateCache();
            }
        });
    }

    /**
     * Adds a certificate to a key. Throws exception, if key cannot be found.
     *
     * @param keyId     the key id
     * @param certBytes the certificate bytes
     */
    public void addTransientCert(String keyId, byte[] certBytes) {
        log.trace("addCert({})", keyId);

        tokenRegistry.writeRun(ctx -> {
            RuntimeKeyImpl key = ctx.findKey(keyId);

            key.addTransientCert(SignerUtil.randomId(), certBytes);
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

        tokenRegistry.writeRun(ctx -> {
            var cert = ctx.getCert(certId);

            assertIsNotTransient(cert);

            try {
                tokenKeyCertWriteService.setActive(cert.id(), active);
            } catch (CodedException signerException) {
                throw signerException;
            } catch (Exception e) {
                throw new SignerException(X_INTERNAL_ERROR, "Failed to set certificate active status for " + certId, e);
            } finally {
                ctx.invalidateCache();
            }
        });
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

        tokenRegistry.writeRun(ctx -> {
            var cert = ctx.getCert(certId);

            assertIsNotTransient(cert);

            try {
                tokenKeyCertWriteService.updateStatus(cert.id(), status);
            } catch (CodedException signerException) {
                throw signerException;
            } catch (Exception e) {
                throw new SignerException(X_INTERNAL_ERROR, "Failed to set certificate status for " + certId, e);
            } finally {
                ctx.invalidateCache();
            }
        });
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

        tokenRegistry.writeRun(ctx -> {
            var cert = ctx.getCert(certId);

            assertIsNotTransient(cert);

            try {
                tokenKeyCertWriteService.updateRenewedCertHash(cert.id(), hash);
            } catch (CodedException signerException) {
                throw signerException;
            } catch (Exception e) {
                throw new SignerException(X_INTERNAL_ERROR, "Failed to set renewed certificate hash for " + certId, e);
            } finally {
                ctx.invalidateCache();
            }
        });
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

        tokenRegistry.writeRun(ctx -> {
            var cert = ctx.getCert(certId);

            assertIsNotTransient(cert);

            try {
                tokenKeyCertWriteService.updateRenewalError(cert.id(), errorMessage);
            } catch (CodedException signerException) {
                throw signerException;
            } catch (Exception e) {
                throw new SignerException(X_INTERNAL_ERROR, "Failed to set renewal error for " + certId, e);
            } finally {
                ctx.invalidateCache();
            }
        });
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

        tokenRegistry.writeRun(ctx -> {
            var cert = ctx.getCert(certId);

            assertIsNotTransient(cert);

            try {
                tokenKeyCertWriteService.updateNextAutomaticRenewalTime(cert.id(), nextRenewalTime);
            } catch (CodedException signerException) {
                throw signerException;
            } catch (Exception e) {
                throw new SignerException(X_INTERNAL_ERROR, "Failed to set next planned renewal time for " + certId, e);
            } finally {
                ctx.invalidateCache();
            }
        });
    }


    /**
     * Removes certificate with given id.
     *
     * @param certId the certificate id
     * @return true if certificate was removed
     */
    public boolean removeCert(String certId) {
        log.trace("removeCert({})", certId);

        return tokenRegistry.writeAction(ctx -> {
            var cert = ctx.findCert(certId);
            if (cert.isEmpty()) {
                log.warn("Certificate with id {} not found", certId);
                return false;
            }
            try {
                return tokenKeyCertWriteService.delete(cert.get().id());
            } catch (CodedException signerException) {
                throw signerException;
            } catch (Exception e) {
                throw new SignerException(X_INTERNAL_ERROR, "Failed to remove certificate " + certId, e);
            } finally {
                ctx.invalidateCache();
            }
        });
    }

    /**
     * Adds a new certificate request to a key.
     *
     * @param externalKeyId       the key id
     * @param memberId    the member id
     * @param subjectName the sbject name
     * @param keyUsage    the key usage
     * @return certificate id
     */
    public String addCertRequest(String externalKeyId,
                                 ClientId.Conf memberId,
                                 String subjectName,
                                 String subjectAltName,
                                 KeyUsageInfo keyUsage,
                                 String certificateProfile) {
        log.trace("addCertRequest({}, {})", externalKeyId, memberId);
        return tokenRegistry.writeAction(ctx -> {
            var key = ctx.findKey(externalKeyId);

            if (key.usage() != null && key.usage() != keyUsage) {
                throw CodedException.tr(X_WRONG_CERT_USAGE,
                        "cert_request_wrong_usage",
                        "Cannot add %s certificate request to %s key", keyUsage,
                        key.usage());
            }
            try {
                updateKeyUsage(key, keyUsage);

                final var existingCertRequestOpt = findExistingCertRequest(key, memberId, subjectName);
                if (existingCertRequestOpt.isPresent()) {
                    String existingCertReqId = existingCertRequestOpt.get();
                    log.warn("Certificate request (memberId: {}, subjectName: {}) already exists", memberId, subjectName);
                    return existingCertReqId;
                }

                return addCertRequest(key, memberId, subjectName, subjectAltName, certificateProfile);
            } finally {
                ctx.invalidateCache();
            }
        });
    }

    private void updateKeyUsage(RuntimeKey key, KeyUsageInfo keyUsage) {
        try {
            tokenKeyWriteService.updateKeyUsage(key.id(), keyUsage);
        } catch (CodedException signerException) {
            throw signerException;
        } catch (Exception e) {
            throw new SignerException(X_INTERNAL_ERROR, "Failed to update key usage for key " + key.externalId(), e);
        }
    }

    private Optional<String> findExistingCertRequest(RuntimeKey key, ClientId.Conf memberId, String subjectName) {
        for (CertRequestData certRequest : key.certRequests()) {
            ClientId crMember = certRequest.memberId();
            String crSubject = certRequest.subjectName();

            if ((memberId == null && crSubject.equalsIgnoreCase(subjectName))
                    || (memberId != null && memberId.equals(crMember)
                    && crSubject.equalsIgnoreCase(subjectName))) {
                return Optional.of(certRequest.externalId());
            }
        }
        return Optional.empty();
    }

    private String addCertRequest(RuntimeKey key,
                                  ClientId.Conf memberId,
                                  String subjectName,
                                  String subjectAltName,
                                  String certificateProfile) {
        try {
            var certReqId = SignerUtil.randomId();
            tokenKeyCertRequestWriteService.save(key.id(), certReqId, memberId, subjectName, subjectAltName, certificateProfile);
            log.info("Added new certificate request [{}] (memberId: {}, subjectId: {}) under key {}",
                    certReqId, memberId, subjectName, key.externalId());
            return certReqId;
        } catch (CodedException signerException) {
            throw signerException;
        } catch (Exception e) {
            throw new SignerException(X_INTERNAL_ERROR, "Failed to add certificate request for key " + key.externalId(), e);
        }
    }

    /**
     * Removes a certificate request with given id.
     *
     * @param certReqId the certificate request id
     * @return key id from which the certificate request was removed
     */
    public boolean removeCertRequest(String certReqId) {
        log.trace("removeCertRequest({})", certReqId);

        return tokenRegistry.writeAction(ctx -> {
            var certReq = ctx.findCertRequest(certReqId);
            if (certReq.isEmpty()) {
                log.warn("Certificate request with id {} not found", certReqId);
                return false;
            }
            try {
                return tokenKeyCertRequestWriteService.delete(certReq.get().id());
            } catch (CodedException signerException) {
                throw signerException;
            } catch (Exception e) {
                throw new SignerException(X_INTERNAL_ERROR, "Failed to remove certificate request " + certReqId, e);
            } finally {
                ctx.invalidateCache();
            }
        });
    }

    private void assertIsNotTransient(RuntimeCert cert) {
        if (cert.isTransientCert()) {
            throw new SignerException(X_INTERNAL_ERROR, "Operation not allowed for transient cert " + cert.externalId());
        }
    }
}
