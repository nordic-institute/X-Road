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
import ee.ria.xroad.common.conf.serverconf.model.CertificateType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.CertUtils;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.facade.GlobalConfFacade;
import org.niis.xroad.restapi.facade.SignerProxyFacade;
import org.niis.xroad.restapi.repository.ClientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.cert.X509Certificate;

import static ee.ria.xroad.common.ErrorCodes.SIGNER_X;
import static ee.ria.xroad.common.ErrorCodes.X_CERT_EXISTS;
import static ee.ria.xroad.common.ErrorCodes.X_CSR_NOT_FOUND;
import static ee.ria.xroad.common.ErrorCodes.X_INCORRECT_CERTIFICATE;
import static ee.ria.xroad.common.ErrorCodes.X_WRONG_CERT_USAGE;
import static org.niis.xroad.restapi.service.KeyService.isCausedByKeyNotFound;

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
     * @param certificateBytes
     * @return
     * @throws GlobalConfService.GlobalConfOutdatedException
     * @throws ClientNotFoundException
     * @throws KeyNotFoundException
     * @throws CertificateImportException other general import failure
     * @throws CertificateAlreadyExistsException
     * @throws IncorrectCertificateException
     * @throws WrongCertificateUsageException
     */
    public CertificateType addCertificate(byte[] certificateBytes) throws GlobalConfService.GlobalConfOutdatedException,
            ClientNotFoundException, KeyNotFoundException, CertificateImportException,
            CertificateAlreadyExistsException, IncorrectCertificateException, WrongCertificateUsageException,
            CsrNotFoundException {
        globalConfService.verifyGlobalConfValidity();
        X509Certificate x509Certificate;
        try {
            x509Certificate = CryptoUtils.readCertificate(certificateBytes);
        } catch (Exception e) {
            throw new CertificateImportException("cannot convert bytes to certificate", e);
        }
        CertificateType certificateType = new CertificateType();
        try {
            String certificateState;
            ClientId clientId = null;
            if (CertUtils.isAuthCert(x509Certificate)) {
                certificateState = CertificateInfo.STATUS_SAVED;
            } else {
                String xroadInstance = globalConfFacade.getInstanceIdentifier();
                clientId = getClientIdForSigningCert(xroadInstance, x509Certificate);
                boolean clientExists = clientRepository.clientExists(clientId, true);
                if (!clientExists) {
                    throw new ClientNotFoundException("client " + clientId.toShortString() + " not found");
                }
                certificateState = CertificateInfo.STATUS_REGISTERED;
            }
            byte[] certBytes = x509Certificate.getEncoded();
            signerProxyFacade.importCert(certBytes, certificateState, clientId);
            certificateType.setData(certBytes);
        } catch (ClientNotFoundException e) {
            throw e;
        } catch (CodedException e) {
            translateCodedExceptions(e);
        } catch (Exception e) {
            // other exceptions such as IOExceptions from reading bytestreams
            throw new RuntimeException("error adding certificate", e);
        }
        return certificateType;
    }

    /**
     * Returns the given certificate owner's client ID.
     * @param instanceIdentifier instance identifier of the owner
     * @param cert the certificate
     * @return certificate owner's client ID
     * @throws CertificateImportException if any errors occur
     */
    private ClientId getClientIdForSigningCert(String instanceIdentifier, X509Certificate cert) throws
            CertificateImportException {
        ClientId dummyClientId = ClientId.create(instanceIdentifier, DUMMY_MEMBER, DUMMY_MEMBER);
        SignCertificateProfileInfoParameters signCertificateProfileInfoParameters =
                new SignCertificateProfileInfoParameters(dummyClientId, DUMMY_MEMBER);
        ClientId certificateSubject;
        try {
            certificateSubject = globalConfFacade.getSubjectName(signCertificateProfileInfoParameters, cert);
        } catch (Exception e) {
            throw new CertificateImportException("Cannot read member identifier from signing certificate", e);
        }
        return certificateSubject;
    }

    /**
     * Helper to translate caught {@link CodedException CodedExceptions}
     * @param e
     * @throws CertificateAlreadyExistsException
     * @throws IncorrectCertificateException
     * @throws WrongCertificateUsageException
     * @throws CsrNotFoundException
     * @throws KeyNotFoundException
     */
    private void translateCodedExceptions(CodedException e) throws CertificateAlreadyExistsException,
            IncorrectCertificateException, WrongCertificateUsageException, CsrNotFoundException, KeyNotFoundException {
        if (isCausedByDuplicateCertificate(e)) {
            throw new CertificateAlreadyExistsException(e);
        } else if (isCausedByIncorrectCertificate(e)) {
            throw new IncorrectCertificateException(e);
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
        return CSR_NOT_FOUND.equals(e.getFaultCode());
    }

    static final String DUPLICATE_CERT_FAULT_CODE = SIGNER_X + "." + X_CERT_EXISTS;
    static final String INCORRECT_CERT_FAULT_CODE = SIGNER_X + "." + X_INCORRECT_CERTIFICATE;
    static final String CERT_WRONG_USAGE_FAULT_CODE = SIGNER_X + "." + X_WRONG_CERT_USAGE;
    static final String CSR_NOT_FOUND = SIGNER_X + "." + X_CSR_NOT_FOUND;

    /**
     * General errors that happen when importing a cert
     */
    public static class CertificateImportException extends ServiceException {
        public static final String ERROR_CERTIFICATE_IMPORT = "certificate_import_failed";

        public CertificateImportException(String msg, Throwable t) {
            super(msg, t, new ErrorDeviation(ERROR_CERTIFICATE_IMPORT));
        }
    }

    /**
     * Error parsing cert
     */
    public static class IncorrectCertificateException extends ServiceException {
        public static final String ERROR_INCORRECT_CERTIFICATE = "certificate_incorrect";

        public IncorrectCertificateException(Throwable t) {
            super(t, new ErrorDeviation(ERROR_INCORRECT_CERTIFICATE));
        }
    }

    /**
     * Cert usage info is wrong (e.g. cert is both auth and sign or neither)
     */
    public static class WrongCertificateUsageException extends ServiceException {
        public static final String ERROR_CERTIFICATE_WRONG_USAGE = "certificate_wrong_usage";

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
}
