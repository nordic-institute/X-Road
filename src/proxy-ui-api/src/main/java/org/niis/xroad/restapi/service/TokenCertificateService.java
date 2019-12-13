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
import ee.ria.xroad.common.certificateprofile.impl.SignCertificateProfileInfoParameters;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.CertUtils;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;

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

import static ee.ria.xroad.common.ErrorCodes.SIGNER_X;
import static ee.ria.xroad.common.ErrorCodes.X_CERT_EXISTS;
import static ee.ria.xroad.common.ErrorCodes.X_CERT_NOT_FOUND;
import static ee.ria.xroad.common.ErrorCodes.X_CSR_NOT_FOUND;
import static ee.ria.xroad.common.ErrorCodes.X_INCORRECT_CERTIFICATE;
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

    @Autowired
    public TokenCertificateService(GlobalConfService globalConfService, GlobalConfFacade globalConfFacade,
            SignerProxyFacade signerProxyFacade, ClientRepository clientRepository) {
        this.globalConfService = globalConfService;
        this.globalConfFacade = globalConfFacade;
        this.signerProxyFacade = signerProxyFacade;
        this.clientRepository = clientRepository;
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
     * @param isFromToken whether the token was read from a token or not
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
     * Activates certificate by given certificateId
     *
     * @param certificateId
     * @throws CertificateNotFoundException
     */
    public void activateCertificate(String certificateId) throws CertificateNotFoundException {
        try {
            signerProxyFacade.activateCert(certificateId);
        } catch (CodedException e) {
            if (isCausedByCertNotFound(e)) {
                throw new CertificateNotFoundException("Certificate with id " + certificateId + " not found");
            } else {
                throw e;
            }
        } catch (Exception e) {
            throw new RuntimeException("certificate activation failed", e);
        }
    }

    /**
     * Deactivates certificate by given certificateId
     *
     * @param certificateId
     * @throws CertificateNotFoundException
     */
    public void deactivateCertificate(String certificateId) throws CertificateNotFoundException {
        try {
            signerProxyFacade.deactivateCert(certificateId);
        } catch (CodedException e) {
            if (isCausedByCertNotFound(e)) {
                throw new CertificateNotFoundException("Certificate with id " + certificateId + " not found");
            } else {
                throw e;
            }
        } catch (Exception e) {
            throw new RuntimeException("certificate deactivation failed", e);
        }
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

    static final String DUPLICATE_CERT_FAULT_CODE = SIGNER_X + "." + X_CERT_EXISTS;
    static final String INCORRECT_CERT_FAULT_CODE = SIGNER_X + "." + X_INCORRECT_CERTIFICATE;
    static final String CERT_WRONG_USAGE_FAULT_CODE = SIGNER_X + "." + X_WRONG_CERT_USAGE;
    static final String CSR_NOT_FOUND_FAULT_CODE = SIGNER_X + "." + X_CSR_NOT_FOUND;
    static final String CERT_NOT_FOUND_FAULT_CODE = SIGNER_X + "." + X_CERT_NOT_FOUND;

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
}
