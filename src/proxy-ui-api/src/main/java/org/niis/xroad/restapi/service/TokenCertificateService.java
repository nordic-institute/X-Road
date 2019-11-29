/**
 * The MIT License
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
package org.niis.xroad.restapi.service;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.certificateprofile.CertificateProfileInfo;
import ee.ria.xroad.common.certificateprofile.DnFieldValue;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.message.GenerateCertRequest;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.facade.SignerProxyFacade;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static ee.ria.xroad.common.ErrorCodes.SIGNER_X;
import static ee.ria.xroad.common.ErrorCodes.X_KEY_NOT_AVAILABLE;
import static ee.ria.xroad.common.ErrorCodes.X_KEY_NOT_FOUND;
import static ee.ria.xroad.common.ErrorCodes.X_TOKEN_NOT_ACTIVE;
import static ee.ria.xroad.common.ErrorCodes.X_TOKEN_NOT_AVAILABLE;
import static ee.ria.xroad.common.ErrorCodes.X_TOKEN_NOT_INITIALIZED;
import static ee.ria.xroad.common.ErrorCodes.X_TOKEN_READONLY;

/**
 * token certificate service
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
public class TokenCertificateService {

    private final SignerProxyFacade signerProxyFacade;
    private final ClientService clientService;
    private final CertificateAuthorityService certificateAuthorityService;
    private final KeyService keyService;
    private final DnFieldHelper dnFieldHelper;

    @Autowired
    public TokenCertificateService(SignerProxyFacade signerProxyFacade, ClientService clientService,
            CertificateAuthorityService certificateAuthorityService,
            KeyService keyService, DnFieldHelper dnFieldHelper) {
        this.signerProxyFacade = signerProxyFacade;
        this.clientService = clientService;
        this.certificateAuthorityService = certificateAuthorityService;
        this.keyService = keyService;
        this.dnFieldHelper = dnFieldHelper;
    }

    /**
     * Create a CSR
     * @param keyId
     * @param memberId
     * @param keyUsage
     * @param caName
     * @param subjectFieldValues user-submitted parameters for subject DN
     * @param format
     * @return csr bytes
     * @throws CertificateAuthorityNotFoundException
     * @throws ClientNotFoundException
     * @throws CertificateProfileInstantiationException
     * @throws WrongKeyUsageException if keyUsage param did not match the key's usage type
     * @throws DnFieldHelper.InvalidDnParameterException if required dn parameters were missing, or if there
     * were some extra parameters
     * @throws KeyService.KeyNotFoundException
     * @throws CsrCreationFailureException when signer could not create CSR for some reason.
     * Subclass {@link KeyNotOperationalException} when the reason is key not being operational.
     */
    public byte[] generateCertRequest(String keyId, ClientId memberId, KeyUsageInfo keyUsage,
            String caName, Map<String, String> subjectFieldValues, GenerateCertRequest.RequestFormat format)
            throws CertificateAuthorityNotFoundException, ClientNotFoundException,
            CertificateProfileInstantiationException, WrongKeyUsageException,
            KeyService.KeyNotFoundException, CsrCreationFailureException,
            DnFieldHelper.InvalidDnParameterException {

        // validate key and memberId existence
        KeyInfo key = keyService.getKey(keyId);

        if (keyUsage == KeyUsageInfo.SIGNING) {
            // validate that the member exists or has a subsystem on this server
            if (!clientService.getLocalClientMemberIds().contains(memberId)) {
                throw new ClientNotFoundException("client with id " + memberId + ", or subsystem for it, not found");
            }
        }

        // check that keyUsage is allowed
        if (key.getUsage() != null) {
            if (key.getUsage() != keyUsage) {
                throw new WrongKeyUsageException();
            }
        }

        CertificateProfileInfo profile = certificateAuthorityService.getCertificateProfile(caName, keyUsage, memberId);

        List<DnFieldValue> dnFieldValues = dnFieldHelper.processDnParameters(profile, subjectFieldValues);

        String subjectName = dnFieldHelper.createSubjectName(dnFieldValues);

        try {
            return signerProxyFacade.generateCertRequest(keyId, memberId,
                    keyUsage, subjectName, format);
        } catch (CodedException e) {
            if (isCausedByKeyNotOperational(e)) {
                throw new KeyNotOperationalException(e);
            } else {
                throw new CsrCreationFailureException(e);
            }
        } catch (Exception e) {
            throw new CsrCreationFailureException(e);
        }
    }

    private static String signerFaultCode(String detail) {
        return SIGNER_X + "." + detail;
    }
    static final Set<String> KEY_NOT_OPERATIONAL_FOR_CSR_FAULT_CODES;
    static {
        KEY_NOT_OPERATIONAL_FOR_CSR_FAULT_CODES = new HashSet<>();
        KEY_NOT_OPERATIONAL_FOR_CSR_FAULT_CODES.add(signerFaultCode(X_KEY_NOT_AVAILABLE));
        // unfortunately signer sends X_KEY_NOT_AVAILABLE as X_KEY_NOT_FOUND
        // we know that key exists, so X_KEY_NOT_FOUND belongs to the set in csr creation context
        KEY_NOT_OPERATIONAL_FOR_CSR_FAULT_CODES.add(signerFaultCode(X_KEY_NOT_FOUND));
        KEY_NOT_OPERATIONAL_FOR_CSR_FAULT_CODES.add(signerFaultCode(X_TOKEN_NOT_ACTIVE));
        KEY_NOT_OPERATIONAL_FOR_CSR_FAULT_CODES.add(signerFaultCode(X_TOKEN_NOT_INITIALIZED));
        KEY_NOT_OPERATIONAL_FOR_CSR_FAULT_CODES.add(signerFaultCode(X_TOKEN_NOT_AVAILABLE));
        KEY_NOT_OPERATIONAL_FOR_CSR_FAULT_CODES.add(signerFaultCode(X_TOKEN_READONLY));
    }

    static boolean isCausedByKeyNotOperational(CodedException e) {
        return KEY_NOT_OPERATIONAL_FOR_CSR_FAULT_CODES.contains(e.getFaultCode());
    }

    /**
     * Thrown if signer failed to create CSR
     */
    public static class CsrCreationFailureException extends ServiceException {
        public static final String ERROR_INVALID_DN_PARAMETER = "csr_creation_failure";

        public CsrCreationFailureException(Throwable t, ErrorDeviation errorDeviation) {
            super(t, errorDeviation);
        }
        public CsrCreationFailureException(Throwable t) {
            super(t, new ErrorDeviation(ERROR_INVALID_DN_PARAMETER));
        }
        public CsrCreationFailureException(String s) {
            super(s, new ErrorDeviation(ERROR_INVALID_DN_PARAMETER));
        }
    }

    /**
     * Thrown if signer failed to create CSR due to key (or token) not being in a state to do so.
     * For example, when key or token is not active.
     */
    public static class KeyNotOperationalException extends CsrCreationFailureException {
        public static final String ERROR_KEY_NOT_OPERATIONAL = "key_not_operational";

        public KeyNotOperationalException(Throwable t) {
            super(t, new ErrorDeviation(ERROR_KEY_NOT_OPERATIONAL));
        }
    }


    /**
     * Cert usage info is wrong (e.g. cert is both auth and sign or neither)
     */
    public static class WrongCertificateUsageException extends ServiceException {
        public static final String ERROR_CERTIFICATE_WRONG_USAGE = "cert_wrong_usage";

        public WrongCertificateUsageException(Throwable t) {
            super(t, new ErrorDeviation(ERROR_CERTIFICATE_WRONG_USAGE));
        }
    }

}
