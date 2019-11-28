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
import ee.ria.xroad.common.certificateprofile.DnFieldDescription;
import ee.ria.xroad.common.certificateprofile.DnFieldValue;
import ee.ria.xroad.common.certificateprofile.impl.DnFieldValueImpl;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.message.GenerateCertRequest;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.facade.GlobalConfFacade;
import org.niis.xroad.restapi.facade.SignerProxyFacade;
import org.niis.xroad.restapi.repository.ClientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final GlobalConfService globalConfService;
    private final GlobalConfFacade globalConfFacade;
    private final SignerProxyFacade signerProxyFacade;
    private final ClientRepository clientRepository;
    private final CertificateAuthorityService certificateAuthorityService;
    private final KeyService keyService;

    @Autowired
    public TokenCertificateService(GlobalConfService globalConfService, GlobalConfFacade globalConfFacade,
            SignerProxyFacade signerProxyFacade, ClientRepository clientRepository,
            CertificateAuthorityService certificateAuthorityService,
            KeyService keyService) {
        this.globalConfService = globalConfService;
        this.globalConfFacade = globalConfFacade;
        this.signerProxyFacade = signerProxyFacade;
        this.clientRepository = clientRepository;
        this.certificateAuthorityService = certificateAuthorityService;
        this.keyService = keyService;
    }

    /**
     * Create a CSR
     * @param keyId
     * @param memberId
     * @param keyUsage
     * @param caName
     * @param distinguishedNameParameters
     * @param format
     * @return csr bytes
     * @throws CertificateAuthorityNotFoundException
     * @throws ClientNotFoundException
     * @throws CertificateProfileInstantiationException
     * @throws WrongKeyUsageException if keyUsage param did not match the key's usage type
     * @throws InvalidDnParameterException if required dn parameters were missing, or if there were some extra
     * parameters
     * @throws KeyService.KeyNotFoundException
     * @throws CsrCreationFailureException when signer could not create CSR for some reason.
     * Subclass {@link KeyNotOperationalException} when the reason is key not being operational.
     */
    public byte[] generateCertRequest(String keyId,
            ClientId memberId,
            KeyUsageInfo keyUsage,
            String caName,
            Map<String, String> distinguishedNameParameters,
            GenerateCertRequest.RequestFormat format)
            throws
            CertificateAuthorityNotFoundException,
            ClientNotFoundException,
            CertificateProfileInstantiationException,
            WrongKeyUsageException, InvalidDnParameterException,
            KeyService.KeyNotFoundException, CsrCreationFailureException {

        // validate key and memberId existence
        KeyInfo key = keyService.getKey(keyId);
        ClientType clientType = null;

        if (keyUsage == KeyUsageInfo.SIGNING) {
            clientType = clientRepository.getClient(memberId);
            if (clientType == null) {
                throw new ClientNotFoundException("client not found: " + memberId);
            }
        }

        // check that keyUsage is allowed
        if (key.getUsage() != null) {
            if (key.getUsage() != keyUsage) {
                throw new WrongKeyUsageException();
            }
        }

        // TO DO: is "subject field" better term for distinguishedNameParameters?

        CertificateProfileInfo profile = certificateAuthorityService.getCertificateProfile(caName, keyUsage, memberId);

        List<DnFieldValue> dnFieldValues = processDnParameters(profile, distinguishedNameParameters);

        String subjectName = createSubjectName(dnFieldValues);

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

    /**
     * Transform dn fields into a subject name
     */
    private String createSubjectName(List<DnFieldValue> dnFieldValues) {
        return dnFieldValues.stream()
                .filter(dnFieldValue -> !StringUtils.isBlank(dnFieldValue.getValue()))
                .map(dnFieldValue -> dnFieldValue.getId() + "=" + dnFieldValue.getValue())
                .collect(Collectors.joining(", "));
    }

    /**
     * Read dn parameters from dnParameters map, match them to DnFieldDescription
     * definitions (consider readOnly, required, etc) and validate that all parameters
     * are fine.
     * @return valid DnFieldValue objects
     * @throws InvalidDnParameterException if there were invalid parameters
     */
    private List<DnFieldValue> processDnParameters(CertificateProfileInfo profile, Map<String, String> dnParameters)
            throws InvalidDnParameterException {
        Set<String> unprocessedParameters = new HashSet<>(dnParameters.keySet());
        List<DnFieldValue> dnValues = new ArrayList<>();
        // match all dn fields with either default values or actual parameters
        for (DnFieldDescription description: profile.getSubjectFields()) {
            String fieldValue = null;
            if (description.isReadOnly() || StringUtils.isBlank(dnParameters.get(description.getId()))) {
                fieldValue = description.getDefaultValue();
            } else {
                fieldValue = dnParameters.get(description.getId());
            }
            dnValues.add(new DnFieldValueImpl(description.getId(), fieldValue));
            unprocessedParameters.remove(description.getId());
        }
        if (!unprocessedParameters.isEmpty()) {
            throw new InvalidDnParameterException("extraneous parameters: " + unprocessedParameters);
        }
        // validate
        for (DnFieldValue dnValue: dnValues) {
            try {
                profile.validateSubjectField(dnValue);
            } catch (Exception e) {
                throw new InvalidDnParameterException(e);
            }
        }
        return dnValues;
    }

    /**
     * Thrown if a dn parameter was invalid
     */
    public static class InvalidDnParameterException extends ServiceException {
        public static final String ERROR_INVALID_DN_PARAMETER = "invalid_dn_parameter";

        public InvalidDnParameterException(Throwable t) {
            super(t, new ErrorDeviation(ERROR_INVALID_DN_PARAMETER));
        }
        public InvalidDnParameterException(String s) {
            super(s, new ErrorDeviation(ERROR_INVALID_DN_PARAMETER));
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
