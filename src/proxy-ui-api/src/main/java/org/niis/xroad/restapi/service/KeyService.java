/**
 * The MIT License
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.restapi.service;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.signer.protocol.dto.CertRequestInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.facade.SignerProxyFacade;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static ee.ria.xroad.common.ErrorCodes.SIGNER_X;
import static ee.ria.xroad.common.ErrorCodes.X_CSR_NOT_FOUND;
import static ee.ria.xroad.common.ErrorCodes.X_KEY_NOT_FOUND;
import static org.niis.xroad.restapi.service.SecurityHelper.verifyAuthority;
import static org.niis.xroad.restapi.service.TokenService.isCausedByTokenNotActive;
import static org.niis.xroad.restapi.service.TokenService.isCausedByTokenNotFound;

/**
 * Service that handles keys
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
public class KeyService {

    private final SignerProxyFacade signerProxyFacade;
    private final TokenService tokenService;

    /**
     * KeyService constructor
     * @param tokenService
     * @param signerProxyFacade
     */
    @Autowired
    public KeyService(TokenService tokenService, SignerProxyFacade signerProxyFacade) {
        this.tokenService = tokenService;
        this.signerProxyFacade = signerProxyFacade;
    }

    /**
     * Return one key
     * @param keyId
     * @return
     * @throws KeyNotFoundException if key was not found
     */
    public KeyInfo getKey(String keyId) throws KeyNotFoundException {
        Collection<TokenInfo> tokens = tokenService.getAllTokens();
        Optional<KeyInfo> keyInfo = tokens.stream()
                .map(TokenInfo::getKeyInfo)
                .flatMap(List::stream)
                .filter(key -> keyId.equals(key.getId()))
                .findFirst();
        if (!keyInfo.isPresent()) {
            throw new KeyNotFoundException("key with id " + keyId + " not found");
        }

        return keyInfo.get();
    }

    /**
     * Finds csr with matching id from KeyInfo, or throws {@link CsrNotFoundException}
     * @throws CsrNotFoundException
     */
    private CertRequestInfo getCsr(KeyInfo keyInfo, String csrId) throws CsrNotFoundException {
        Optional<CertRequestInfo> csr = keyInfo.getCertRequests().stream()
                .filter(csrInfo -> csrInfo.getId().equals(csrId))
                .findFirst();
        if (!csr.isPresent()) {
            throw new CsrNotFoundException("csr with id " + csrId + " not found");
        }
        return csr.get();
    }

    public KeyInfo updateKeyFriendlyName(String id, String friendlyName) throws KeyNotFoundException {
        KeyInfo keyInfo = null;
        try {
            signerProxyFacade.setKeyFriendlyName(id, friendlyName);
            keyInfo = getKey(id);
        } catch (KeyNotFoundException e) {
            throw e;
        } catch (CodedException e) {
            if (isCausedByKeyNotFound(e)) {
                throw new KeyNotFoundException(e);
            } else {
                throw e;
            }
        } catch (Exception e) {
            throw new RuntimeException("Update key friendly name failed", e);
        }

        return keyInfo;
    }

    /**
     * Generate a new key for selected token
     * @param tokenId
     * @param keyLabel
     * @return {@link KeyInfo}
     * @throws TokenNotFoundException
     */
    public KeyInfo addKey(String tokenId, String keyLabel) throws TokenNotFoundException,
            TokenService.TokenNotActiveException {
        KeyInfo keyInfo = null;
        try {
            keyInfo = signerProxyFacade.generateKey(tokenId, keyLabel);
        } catch (CodedException e) {
            if (isCausedByTokenNotFound(e)) {
                throw new TokenNotFoundException(e);
            } else if (isCausedByTokenNotActive(e)) {
                throw new TokenService.TokenNotActiveException(e);
            } else {
                throw e;
            }
        } catch (Exception other) {
            throw new RuntimeException("adding a new key failed", other);
        }
        return keyInfo;
    }

    static boolean isCausedByKeyNotFound(CodedException e) {
        return KEY_NOT_FOUND_FAULT_CODE.equals(e.getFaultCode());
    }

    static boolean isCausedByCsrNotFound(CodedException e) {
        return CSR_NOT_FOUND_FAULT_CODE.equals(e.getFaultCode());
    }

    private static String signerFaultCode(String detail) {
        return SIGNER_X + "." + detail;
    }

    static final String KEY_NOT_FOUND_FAULT_CODE = signerFaultCode(X_KEY_NOT_FOUND);
    static final String CSR_NOT_FOUND_FAULT_CODE = signerFaultCode(X_CSR_NOT_FOUND);

    public void deleteCsr(String keyId, String csrId) throws KeyNotFoundException, CsrNotFoundException {
        KeyInfo keyInfo = getKey(keyId);
        CertRequestInfo csrInfo = getCsr(keyInfo, csrId);

        if (keyInfo.isForSigning()) {
            verifyAuthority("DELETE_SIGN_CERT");
        } else {
            verifyAuthority("DELETE_AUTH_CERT");
        }
        try {
            signerProxyFacade.deleteCertRequest(csrId);
        } catch (CodedException e) {
            if (isCausedByCsrNotFound(e)) {
                throw new CsrNotFoundException(e);
            } else {
                throw e;
            }
        } catch (Exception other) {
            throw new RuntimeException("deleting a csr failed", other);
        }
    }

    public static class CsrNotFoundException extends NotFoundException {
        public static final String ERROR_CSR_NOT_FOUND = "csr_not_found";

        public CsrNotFoundException(String s) {
            super(s, createError());        }

        public CsrNotFoundException(Throwable t) {
            super(t, createError());
        }

        private static ErrorDeviation createError() {
            return new ErrorDeviation(ERROR_CSR_NOT_FOUND);
        }
    }

}
