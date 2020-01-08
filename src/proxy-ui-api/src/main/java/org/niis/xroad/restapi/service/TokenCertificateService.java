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
import ee.ria.xroad.common.certificateprofile.impl.SignCertificateProfileInfoParameters;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.util.CertUtils;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.message.GenerateCertRequest;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.facade.GlobalConfFacade;
import org.niis.xroad.restapi.facade.SignerProxyFacade;
import org.niis.xroad.restapi.repository.ClientRepository;
import org.niis.xroad.restapi.util.FormatUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static ee.ria.xroad.common.ErrorCodes.SIGNER_X;
import static ee.ria.xroad.common.ErrorCodes.X_CERT_EXISTS;
import static ee.ria.xroad.common.ErrorCodes.X_CERT_NOT_FOUND;
import static ee.ria.xroad.common.ErrorCodes.X_CSR_NOT_FOUND;
import static ee.ria.xroad.common.ErrorCodes.X_INCORRECT_CERTIFICATE;
import static ee.ria.xroad.common.ErrorCodes.X_KEY_NOT_AVAILABLE;
import static ee.ria.xroad.common.ErrorCodes.X_KEY_NOT_FOUND;
import static ee.ria.xroad.common.ErrorCodes.X_TOKEN_NOT_ACTIVE;
import static ee.ria.xroad.common.ErrorCodes.X_TOKEN_NOT_AVAILABLE;
import static ee.ria.xroad.common.ErrorCodes.X_TOKEN_NOT_INITIALIZED;
import static ee.ria.xroad.common.ErrorCodes.X_TOKEN_READONLY;
import static ee.ria.xroad.common.ErrorCodes.X_WRONG_CERT_USAGE;
import static org.niis.xroad.restapi.service.KeyService.isCausedByKeyNotFound;
import static org.niis.xroad.restapi.service.SecurityHelper.verifyAuthority;

/**
 * token certificate service
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
public class TokenCertificateService {

    private static final String DUMMY_MEMBER = "dummy";

    private final GlobalConfService globalConfService;
    private final GlobalConfFacade globalConfFacade;
    private final SignerProxyFacade signerProxyFacade;
    private final ClientRepository clientRepository;
    private final ManagementRequestSenderService managementRequestSenderService;
    private final ServerConfService serverConfService;
    private final ClientService clientService;
    private final CertificateAuthorityService certificateAuthorityService;
    private final KeyService keyService;
    private final DnFieldHelper dnFieldHelper;

    @Autowired
    public TokenCertificateService(SignerProxyFacade signerProxyFacade, ClientService clientService,
            CertificateAuthorityService certificateAuthorityService,
            KeyService keyService, DnFieldHelper dnFieldHelper,
            GlobalConfService globalConfService,
            GlobalConfFacade globalConfFacade,
            ClientRepository clientRepository,
            ManagementRequestSenderService managementRequestSenderService, ServerConfService serverConfService) {
        this.signerProxyFacade = signerProxyFacade;
        this.clientService = clientService;
        this.certificateAuthorityService = certificateAuthorityService;
        this.keyService = keyService;
        this.dnFieldHelper = dnFieldHelper;
        this.globalConfService = globalConfService;
        this.globalConfFacade = globalConfFacade;
        this.clientRepository = clientRepository;
        this.managementRequestSenderService = managementRequestSenderService;
        this.serverConfService = serverConfService;
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
     * @throws KeyNotFoundException
     * @throws CsrCreationFailureException when signer could not create CSR for some reason.
     * Subclass {@link KeyNotOperationalException} when the reason is key not being operational.
     */
    public byte[] generateCertRequest(String keyId, ClientId memberId, KeyUsageInfo keyUsage,
            String caName, Map<String, String> subjectFieldValues, GenerateCertRequest.RequestFormat format)
            throws CertificateAuthorityNotFoundException, ClientNotFoundException,
            CertificateProfileInstantiationException, WrongKeyUsageException,
            KeyNotFoundException, CsrCreationFailureException,
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

        /**
         * Carries original CodedError errorCode as metadata
         * @param e
         */
        public KeyNotOperationalException(CodedException e) {
            super(e, new ErrorDeviation(ERROR_KEY_NOT_OPERATIONAL, e.getFaultCode()));
        }
    }

    /**
     * Find an existing cert from a token by it's hash
     * @param hash cert hash of an existing cert. Will be transformed to lowercase
     * @return
     * @throws CertificateNotFoundException
     */
    public CertificateInfo getCertificateInfo(String hash) throws CertificateNotFoundException {
        CertificateInfo certificateInfo = null;
        try {
            certificateInfo = signerProxyFacade.getCertForHash(hash.toLowerCase()); // lowercase needed in Signer
        } catch (CodedException e) {
            if (isCausedByCertNotFound(e)) {
                throw new CertificateNotFoundException("Certificate with hash " + hash + " not found");
            } else {
                throw e;
            }
        } catch (Exception e) {
            throw new RuntimeException("error getting certificate", e);
        }
        return certificateInfo;
    }

    /**
     * Find an existing cert from a token (e.g. HSM) by cert hash and import it to keyconf.xml. This enables the cert
     * to be used for signing messages.
     * @param hash cert hash of an existing cert
     * @return CertificateType
     * @throws CertificateNotFoundException
     * @throws InvalidCertificateException other general import failure
     * @throws GlobalConfService.GlobalConfOutdatedException
     * @throws KeyNotFoundException
     * @throws CertificateAlreadyExistsException
     * @throws WrongCertificateUsageException
     * @throws ClientNotFoundException
     * @throws CsrNotFoundException
     * @throws AuthCertificateNotSupportedException if trying to import an auth cert from a token
     */
    public CertificateInfo importCertificateFromToken(String hash) throws CertificateNotFoundException,
            InvalidCertificateException, GlobalConfService.GlobalConfOutdatedException, KeyNotFoundException,
            CertificateAlreadyExistsException, WrongCertificateUsageException, ClientNotFoundException,
            CsrNotFoundException, AuthCertificateNotSupportedException {
        CertificateInfo certificateInfo = getCertificateInfo(hash);
        return importCertificate(certificateInfo.getCertificateBytes(), true);
    }

    /**
     * Import a cert that is found from a token by it's bytes
     * @param certificateBytes
     * @param isFromToken whether the cert was read from a token or not
     * @return CertificateType
     * @throws GlobalConfService.GlobalConfOutdatedException
     * @throws KeyNotFoundException
     * @throws InvalidCertificateException other general import failure
     * @throws CertificateAlreadyExistsException
     * @throws WrongCertificateUsageException
     * @throws AuthCertificateNotSupportedException if trying to import an auth cert from a token
     */
    private CertificateInfo importCertificate(byte[] certificateBytes, boolean isFromToken)
            throws GlobalConfService.GlobalConfOutdatedException, KeyNotFoundException, InvalidCertificateException,
            CertificateAlreadyExistsException, WrongCertificateUsageException, CsrNotFoundException,
            AuthCertificateNotSupportedException, ClientNotFoundException {
        globalConfService.verifyGlobalConfValidity();
        X509Certificate x509Certificate = null;
        CertificateInfo certificateInfo = null;
        try {
            x509Certificate = CryptoUtils.readCertificate(certificateBytes);
        } catch (Exception e) {
            throw new InvalidCertificateException("cannot convert bytes to certificate", e);
        }
        try {
            String certificateState;
            ClientId clientId = null;
            boolean isAuthCert = CertUtils.isAuthCert(x509Certificate);
            if (isAuthCert) {
                verifyAuthority("IMPORT_AUTH_CERT");
                if (isFromToken) {
                    throw new AuthCertificateNotSupportedException("auth cert cannot be imported from a token");
                }
                certificateState = CertificateInfo.STATUS_SAVED;
            } else {
                verifyAuthority("IMPORT_SIGN_CERT");
                String xroadInstance = globalConfFacade.getInstanceIdentifier();
                clientId = getClientIdForSigningCert(xroadInstance, x509Certificate);
                boolean clientExists = clientRepository.clientExists(clientId, true);
                if (!clientExists) {
                    throw new ClientNotFoundException("client " + clientId.toShortString() + " not found",
                            FormatUtils.xRoadIdToEncodedId(clientId));
                }
                certificateState = CertificateInfo.STATUS_REGISTERED;
            }
            byte[] certBytes = x509Certificate.getEncoded();
            signerProxyFacade.importCert(certBytes, certificateState, clientId);
            String hash = CryptoUtils.calculateCertHexHash(certBytes);
            certificateInfo = getCertificateInfo(hash);
        } catch (ClientNotFoundException | AccessDeniedException | AuthCertificateNotSupportedException e) {
            throw e;
        } catch (CodedException e) {
            translateCodedExceptions(e);
        } catch (Exception e) {
            // something went really wrong
            throw new RuntimeException("error importing certificate", e);
        }
        return certificateInfo;
    }

    /**
     * Import a cert from given bytes. If importing an existing cert from a token use
     * {@link #importCertificateFromToken(String hash)}
     * @param certificateBytes
     * @return CertificateType
     * @throws GlobalConfService.GlobalConfOutdatedException
     * @throws ClientNotFoundException
     * @throws KeyNotFoundException
     * @throws InvalidCertificateException other general import failure
     * @throws CertificateAlreadyExistsException
     * @throws WrongCertificateUsageException
     * @throws AuthCertificateNotSupportedException if trying to import an auth cert from a token
     */
    public CertificateInfo importCertificate(byte[] certificateBytes) throws InvalidCertificateException,
            GlobalConfService.GlobalConfOutdatedException, KeyNotFoundException, CertificateAlreadyExistsException,
            WrongCertificateUsageException, ClientNotFoundException, CsrNotFoundException,
            AuthCertificateNotSupportedException {
        return importCertificate(certificateBytes, false);
    }

    /**
     * Returns the given certificate owner's client ID.
     * @param instanceIdentifier instance identifier of the owner
     * @param cert the certificate
     * @return certificate owner's client ID
     * @throws InvalidCertificateException if any errors occur
     */
    private ClientId getClientIdForSigningCert(String instanceIdentifier, X509Certificate cert)
            throws InvalidCertificateException {
        ClientId dummyClientId = ClientId.create(instanceIdentifier, DUMMY_MEMBER, DUMMY_MEMBER);
        SignCertificateProfileInfoParameters signCertificateProfileInfoParameters =
                new SignCertificateProfileInfoParameters(dummyClientId, DUMMY_MEMBER);
        ClientId certificateSubject;
        try {
            certificateSubject = globalConfFacade.getSubjectName(signCertificateProfileInfoParameters, cert);
        } catch (Exception e) {
            throw new InvalidCertificateException("Cannot read member identifier from signing certificate", e);
        }
        return certificateSubject;
    }

    /**
     * Send the authentication certificate registration request to central server
     * @param hash certificate hash
     * @param securityServerAddress IP address or DNS name of the security server
     * @throws CertificateNotFoundException
     * @throws GlobalConfService.GlobalConfOutdatedException
     */
    public void registerAuthCert(String hash, String securityServerAddress) throws CertificateNotFoundException,
            GlobalConfService.GlobalConfOutdatedException, InvalidCertificateException,
            SignCertificateNotSupportedException {
        CertificateInfo certificateInfo = getCertificateInfo(hash);
        verifyAuthCert(certificateInfo);
        SecurityServerId securityServerId = serverConfService.getSecurityServerId();
        try {
            managementRequestSenderService.sendAuthCertRegisterRequest(securityServerId, securityServerAddress,
                    certificateInfo.getCertificateBytes());
            signerProxyFacade.setCertStatus(certificateInfo.getId(), CertificateInfo.STATUS_REGINPROG);
        } catch (GlobalConfService.GlobalConfOutdatedException | CodedException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Could not set certificate status", e);
        }
    }

    /**
     * Send the authentication certificate deletion request to central server
     * @param hash certificate hash
     * @throws CertificateNotFoundException
     * @throws GlobalConfService.GlobalConfOutdatedException
     */
    public void unregisterAuthCert(String hash) throws CertificateNotFoundException,
            GlobalConfService.GlobalConfOutdatedException, InvalidCertificateException,
            SignCertificateNotSupportedException {
        CertificateInfo certificateInfo = getCertificateInfo(hash);
        verifyAuthCert(certificateInfo);
        SecurityServerId securityServerId = serverConfService.getSecurityServerId();
        try {
            managementRequestSenderService.sendAuthCertDeletionRequest(securityServerId,
                    certificateInfo.getCertificateBytes());
            signerProxyFacade.setCertStatus(certificateInfo.getId(), CertificateInfo.STATUS_DELINPROG);
        } catch (GlobalConfService.GlobalConfOutdatedException | CodedException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Could not set certificate status", e);
        }
    }

    private void verifyAuthCert(CertificateInfo certificateInfo)
            throws SignCertificateNotSupportedException, InvalidCertificateException {
        boolean isAuthCert;
        X509Certificate certificate = null;
        try {
            certificate = CryptoUtils.readCertificate(certificateInfo.getCertificateBytes());
            isAuthCert = CertUtils.isAuthCert(certificate);
            if (!isAuthCert) {
                throw new SignCertificateNotSupportedException("not an auth cert");
            }
        } catch (SignCertificateNotSupportedException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidCertificateException("invalid certificate", e);
        }
    }

    /**
     * Helper to translate caught {@link CodedException CodedExceptions}
     * @param e
     * @throws CertificateAlreadyExistsException
     * @throws InvalidCertificateException
     * @throws WrongCertificateUsageException
     * @throws CsrNotFoundException
     * @throws KeyNotFoundException
     */
    private void translateCodedExceptions(CodedException e) throws CertificateAlreadyExistsException,
            InvalidCertificateException, WrongCertificateUsageException, CsrNotFoundException, KeyNotFoundException {
        if (isCausedByDuplicateCertificate(e)) {
            throw new CertificateAlreadyExistsException(e);
        } else if (isCausedByIncorrectCertificate(e)) {
            throw new InvalidCertificateException(e);
        } else if (isCausedByCertificateWrongUsage(e)) {
            throw new WrongCertificateUsageException(e);
        } else if (isCausedByCsrNotFound(e)) {
            throw new CsrNotFoundException(e);
        } else if (isCausedByKeyNotFound(e)) {
            throw new KeyNotFoundException(e);
        } else {
            throw e;
        }
    }

    static boolean isCausedByDuplicateCertificate(CodedException e) {
        return DUPLICATE_CERT_FAULT_CODE.equals(e.getFaultCode());
    }

    static boolean isCausedByIncorrectCertificate(CodedException e) {
        return INCORRECT_CERT_FAULT_CODE.equals(e.getFaultCode());
    }

    static boolean isCausedByCertificateWrongUsage(CodedException e) {
        return CERT_WRONG_USAGE_FAULT_CODE.equals(e.getFaultCode());
    }

    static boolean isCausedByCsrNotFound(CodedException e) {
        return CSR_NOT_FOUND_FAULT_CODE.equals(e.getFaultCode());
    }

    static boolean isCausedByCertNotFound(CodedException e) {
        return CERT_NOT_FOUND_FAULT_CODE.equals(e.getFaultCode());
    }

    static final String DUPLICATE_CERT_FAULT_CODE = signerFaultCode(X_CERT_EXISTS);
    static final String INCORRECT_CERT_FAULT_CODE = signerFaultCode(X_INCORRECT_CERTIFICATE);
    static final String CERT_WRONG_USAGE_FAULT_CODE = signerFaultCode(X_WRONG_CERT_USAGE);
    static final String CSR_NOT_FOUND_FAULT_CODE = signerFaultCode(X_CSR_NOT_FOUND);
    static final String CERT_NOT_FOUND_FAULT_CODE = signerFaultCode(X_CERT_NOT_FOUND);

    /**
     * General error that happens when importing a cert. Usually a wrong file type
     */
    public static class InvalidCertificateException extends ServiceException {
        public static final String INVALID_CERT = "invalid_cert";

        public InvalidCertificateException(Throwable t) {
            super(t, new ErrorDeviation(INVALID_CERT));
        }

        public InvalidCertificateException(String msg, Throwable t) {
            super(msg, t, new ErrorDeviation(INVALID_CERT));
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

    /**
     * Certificate sign request not found
     */
    public static class CsrNotFoundException extends ServiceException {
        public static final String ERROR_CSR_NOT_FOUND = "csr_not_found";

        public CsrNotFoundException(Throwable t) {
            super(t, new ErrorDeviation(ERROR_CSR_NOT_FOUND));
        }
    }

    /**
     * Probably a rare case of when importing an auth cert from an HSM
     */
    public static class AuthCertificateNotSupportedException extends ServiceException {
        public static final String AUTH_CERT_NOT_SUPPORTED = "auth_cert_not_supported";

        public AuthCertificateNotSupportedException(String msg) {
            super(msg, new ErrorDeviation(AUTH_CERT_NOT_SUPPORTED));
        }
    }

    /**
     * When trying to register a sign cert
     */
    public static class SignCertificateNotSupportedException extends ServiceException {
        public static final String SIGN_CERT_NOT_SUPPORTED = "sign_cert_not_supported";

        public SignCertificateNotSupportedException(String msg) {
            super(msg, new ErrorDeviation(SIGN_CERT_NOT_SUPPORTED));
        }
    }
}
