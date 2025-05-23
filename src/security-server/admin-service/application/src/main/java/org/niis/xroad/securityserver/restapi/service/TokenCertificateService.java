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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.certificateprofile.CertificateProfileInfo;
import ee.ria.xroad.common.certificateprofile.DnFieldValue;
import ee.ria.xroad.common.certificateprofile.impl.SignCertificateProfileInfoParameters;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.util.CertUtils;
import ee.ria.xroad.common.util.CryptoUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.acme.AcmeService;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.common.exception.InternalServerErrorException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.model.ApprovedCAInfo;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.AuditEventHelper;
import org.niis.xroad.restapi.config.audit.RestApiAuditEvent;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.niis.xroad.restapi.exceptions.DeviationAwareRuntimeException;
import org.niis.xroad.restapi.util.SecurityHelper;
import org.niis.xroad.securityserver.restapi.repository.ClientRepository;
import org.niis.xroad.securityserver.restapi.util.MailNotificationHelper;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.signer.api.dto.CertRequestInfo;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.api.dto.TokenInfoAndKeyId;
import org.niis.xroad.signer.api.exception.SignerException;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.proto.CertificateRequestFormat;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.util.CertUtils.getCommonName;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.niis.xroad.common.exception.util.CommonDeviationMessage.INTERNAL_ERROR;
import static org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage.AUTH_CERT_NOT_SUPPORTED;
import static org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage.CERTIFICATE_NOT_FOUND_WITH_ID;
import static org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage.CERTIFICATE_WRONG_USAGE;
import static org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage.SIGN_CERT_NOT_SUPPORTED;

/**
 * token certificate service
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class TokenCertificateService {

    private static final String DUMMY_MEMBER = "dummy";
    private static final String NOT_FOUND = "not found";
    private static final String IMPORT_AUTH_CERT = "IMPORT_AUTH_CERT";
    private static final String IMPORT_SIGN_CERT = "IMPORT_SIGN_CERT";
    private static final String DN_SUBJECT_ALT_NAME = "subjectAltName";

    private final GlobalConfService globalConfService;
    private final GlobalConfProvider globalConfProvider;
    private final ServerConfProvider serverConfProvider;
    private final SignerRpcClient signerRpcClient;
    private final ClientRepository clientRepository;
    private final ManagementRequestSenderService managementRequestSenderService;
    private final ClientService clientService;
    private final CertificateAuthorityService certificateAuthorityService;
    private final KeyService keyService;
    private final DnFieldHelper dnFieldHelper;
    private final PossibleActionsRuleEngine possibleActionsRuleEngine;
    private final TokenService tokenService;
    private final SecurityHelper securityHelper;
    private final AuditDataHelper auditDataHelper;
    private final AuditEventHelper auditEventHelper;
    private final AcmeService acmeService;
    private final MailNotificationHelper mailNotificationHelper;
    private final ServerConfService serverConfService;

    /**
     * Create a CSR
     * @param keyId
     * @param memberId
     * @param keyUsage
     * @param caName
     * @param subjectFieldValues user-submitted parameters for subject DN
     * @param format
     * @param isAcmeOrder
     * @return GeneratedCertRequestInfo containing details and bytes of the cert request
     * @throws CertificateAuthorityNotFoundException     if ca authority with name {@code caName} does not exist
     * @throws ClientNotFoundException                   if client with {@code memberId} id was not found
     * @throws KeyNotFoundException                      if key with {@code keyId} was not found
     * @throws WrongKeyUsageException                    if keyUsage param did not match the key's usage type
     * @throws DnFieldHelper.InvalidDnParameterException if required dn parameters were missing, or if there
     *                                                   were some extra parameters
     * @throws ActionNotPossibleException                if generate csr was not possible for this key
     */
    public SignerRpcClient.GeneratedCertRequestInfo generateCertRequest(String keyId, ClientId.Conf memberId, KeyUsageInfo keyUsage,
                                                                        String caName, Map<String, String> subjectFieldValues,
                                                                        CertificateRequestFormat format, Boolean isAcmeOrder)
            throws CertificateAuthorityNotFoundException, ClientNotFoundException,
                   WrongKeyUsageException,
                   KeyNotFoundException,
                   DnFieldHelper.InvalidDnParameterException, ActionNotPossibleException,
                   CertificateAlreadyExistsException, GlobalConfOutdatedException, CsrNotFoundException,
                   WrongCertificateUsageException, InvalidCertificateException, AuthCertificateNotSupportedException {

        // validate key and memberId existence
        TokenInfo tokenInfo = tokenService.getTokenForKeyId(keyId);
        auditDataHelper.put(tokenInfo);
        KeyInfo key = keyService.getKey(tokenInfo, keyId);
        auditDataHelper.put(key);
        auditDataHelper.put(RestApiAuditProperty.KEY_USAGE, keyUsage);
        auditDataHelper.put(memberId);
        auditDataHelper.put(RestApiAuditProperty.IS_ACME_ORDER, isAcmeOrder);

        if (keyUsage == KeyUsageInfo.SIGNING) {
            // validate that the member exists or has a subsystem on this server
            if (!clientService.getLocalClientMemberIds().contains(memberId)) {
                throw new ClientNotFoundException("client with id " + memberId + ", or subsystem for it, " + NOT_FOUND);
            }
        }

        // check that keyUsage is allowed
        if (key.getUsage() != null) {
            if (key.getUsage() != keyUsage) {
                throw new WrongKeyUsageException();
            }
        }

        // validate that generate csr is possible
        if (keyUsage == KeyUsageInfo.SIGNING) {
            possibleActionsRuleEngine.requirePossibleKeyAction(PossibleActionEnum.GENERATE_SIGN_CSR,
                    tokenInfo, key);
        } else {
            possibleActionsRuleEngine.requirePossibleKeyAction(PossibleActionEnum.GENERATE_AUTH_CSR,
                    tokenInfo, key);
        }

        CertificateProfileInfo profile = certificateAuthorityService.getCertificateProfile(caName, keyUsage, memberId, false);

        String subjectAltName = null;
        if (subjectFieldValues.get(DN_SUBJECT_ALT_NAME) != null
                && !subjectFieldValues.get(DN_SUBJECT_ALT_NAME).isEmpty()) {
            // Set subject alternative name (SAN)
            subjectAltName = subjectFieldValues.get(DN_SUBJECT_ALT_NAME);
            auditDataHelper.put(RestApiAuditProperty.SUBJECT_ALT_NAME, subjectAltName);
        }
        List<DnFieldValue> dnFieldValues = dnFieldHelper.processDnParameters(profile, subjectFieldValues);
        // Remove subjectAltName from dn field values since it's not a valid field value
        List<DnFieldValue> filteredDnFieldValues = dnFieldValues.stream().filter(v -> !v.getId().equals(DN_SUBJECT_ALT_NAME))
                .collect(Collectors.toList());

        String subjectName = dnFieldHelper.createSubjectName(filteredDnFieldValues);
        auditDataHelper.put(RestApiAuditProperty.SUBJECT_NAME, subjectName);
        auditDataHelper.put(RestApiAuditProperty.CERTIFICATION_SERVICE_NAME, caName);
        auditDataHelper.put(RestApiAuditProperty.CSR_FORMAT, format);

        ApprovedCAInfo caInfo = certificateAuthorityService.getCertificateAuthorityInfo(caName);
        SignerRpcClient.GeneratedCertRequestInfo generatedCertRequestInfo;
        try {
            generatedCertRequestInfo = signerRpcClient.generateCertRequest(keyId, memberId,
                    keyUsage, subjectName, subjectAltName, format, caInfo.getCertificateProfileInfo());
        } catch (CodedException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException("Generate cert request failed", e, INTERNAL_ERROR.build());
        }
        if (isAcmeOrder && caInfo.getAcmeServerDirectoryUrl() != null) {
            acmeOrderAndImportCert(memberId, keyUsage, subjectFieldValues, subjectAltName, caInfo, generatedCertRequestInfo);
        }
        return generatedCertRequestInfo;
    }

    private void acmeOrderAndImportCert(ClientId.Conf memberId,
                                        KeyUsageInfo keyUsage,
                                        Map<String, String> subjectFieldValues,
                                        String subjectAltName,
                                        ApprovedCAInfo caInfo,
                                        SignerRpcClient.GeneratedCertRequestInfo generatedCertRequestInfo)
            throws GlobalConfOutdatedException, KeyNotFoundException, InvalidCertificateException, CertificateAlreadyExistsException,
                   WrongCertificateUsageException, CsrNotFoundException, AuthCertificateNotSupportedException, ClientNotFoundException {
        String memberEncodedId = keyUsage == KeyUsageInfo.SIGNING
                ? memberId.asEncodedId()
                : serverConfProvider.getIdentifier().getOwner().asEncodedId();
        List<X509Certificate> chain = acmeService.orderCertificateFromACMEServer(
                subjectFieldValues.get("CN"), subjectAltName, keyUsage, caInfo, memberEncodedId, generatedCertRequestInfo.certRequest());
        if (chain != null) {
            log.info("Acme order was successful, importing certificate");
            try {
                importCertificate(chain.getFirst().getEncoded(), false, true);
            } catch (CertificateEncodingException e) {
                throw new InvalidCertificateException(e);
            }
        }
    }

    /**
     * Regenerate a csr. Regenerate is used by download -endpoint.
     * Regenerate will find an existing csr from TokenManager, and
     * regenerate a new csr binary for it. TokenManager itself, and the csr
     * info stored inside it, will be unchanged.
     * <p>
     * Permissions and possible actions use the values for generate csr,
     * there are no separate values for this operation.
     * @param keyId
     * @param csrId
     * @param format
     * @return GeneratedCertRequestInfo containing details and bytes of the cert request
     * @throws KeyNotFoundException       if key with keyId was not found
     * @throws CsrNotFoundException       if csr with csrId was not found
     * @throws ActionNotPossibleException if regenerate was not possible
     */
    public SignerRpcClient.GeneratedCertRequestInfo regenerateCertRequest(String keyId, String csrId, CertificateRequestFormat format)
            throws KeyNotFoundException, CsrNotFoundException, ActionNotPossibleException {

        // validate key and memberId existence
        TokenInfo tokenInfo = tokenService.getTokenForKeyId(keyId);
        KeyInfo keyInfo = keyService.getKey(tokenInfo, keyId);

        // currently regenerate is part of "generate csr" or
        // "combo generate key + csr" operations in the web application.
        // There are no separate permissions or possible action rules for
        // downloading the csr.
        // Downloading a csr means actually re-generating it, so the
        // possible action rules for it would be very close, if not identical,
        // to generate csr.

        // check usage type specific auth in service, since controller does not know usage type
        if (keyInfo.isForSigning()) {
            securityHelper.verifyAuthority("GENERATE_SIGN_CERT_REQ");
        } else {
            securityHelper.verifyAuthority("GENERATE_AUTH_CERT_REQ");
        }

        // validate that regenerate csr is a possible action
        if (keyInfo.isForSigning()) {
            possibleActionsRuleEngine.requirePossibleKeyAction(PossibleActionEnum.GENERATE_SIGN_CSR,
                    tokenInfo, keyInfo);
        } else {
            possibleActionsRuleEngine.requirePossibleKeyAction(PossibleActionEnum.GENERATE_AUTH_CSR,
                    tokenInfo, keyInfo);
        }

        try {
            return signerRpcClient.regenerateCertRequest(csrId, format);
        } catch (CodedException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException("Regenerate cert request failed", e, INTERNAL_ERROR.build());
        }
    }

    /**
     * Find an existing cert from a token by its hash
     * @param hash cert hash of an existing cert. Will be transformed to lowercase
     * @return
     * @throws CertificateNotFoundException
     */
    public CertificateInfo getCertificateInfo(String hash) throws CertificateNotFoundException {
        CertificateInfo certificateInfo = null;
        try {
            certificateInfo = signerRpcClient.getCertForHash(hash);
        } catch (CodedException e) {
            if (e instanceof SignerException se && se.isCausedByCertNotFound()) {
                throw new CertificateNotFoundException("Certificate with hash " + hash + " " + NOT_FOUND, e);
            }
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException("Error getting certificate", e, INTERNAL_ERROR.build());
        }
        return certificateInfo;
    }

    /**
     * Find an existing cert from a token (e.g. HSM) by cert hash and import it to keyconf.xml. This enables the cert
     * to be used for signing messages.
     * @param hash cert hash of an existing cert
     * @return CertificateInfo
     * @throws CertificateNotFoundException
     * @throws InvalidCertificateException          other general import failure
     * @throws GlobalConfOutdatedException
     * @throws KeyNotFoundException
     * @throws CertificateAlreadyExistsException
     * @throws WrongCertificateUsageException
     * @throws ClientNotFoundException
     * @throws CsrNotFoundException
     * @throws AuthCertificateNotSupportedException if trying to import an auth cert from a token
     * @throws ActionNotPossibleException           if import was not possible due to cert/key/token states
     */
    public CertificateInfo importCertificateFromToken(String hash)
            throws CertificateNotFoundException, InvalidCertificateException,
                   GlobalConfOutdatedException, KeyNotFoundException,
                   CertificateAlreadyExistsException, WrongCertificateUsageException,
                   ClientNotFoundException, CsrNotFoundException,
                   AuthCertificateNotSupportedException, ActionNotPossibleException {
        CertificateInfo certificateInfo = getCertificateInfo(hash);

        TokenInfoAndKeyId tokenInfoAndKeyId = tokenService.getTokenAndKeyIdForCertificateHash(hash);
        TokenInfo tokenInfo = tokenInfoAndKeyId.getTokenInfo();
        KeyInfo keyInfo = tokenInfoAndKeyId.getKeyInfo();
        auditDataHelper.put(tokenInfo);
        auditDataHelper.put(keyInfo);
        auditDataHelper.put(RestApiAuditProperty.CERT_ID, certificateInfo.getId());

        EnumSet<PossibleActionEnum> possibleActions =
                getPossibleActionsForCertificateInternal(hash, certificateInfo, keyInfo, tokenInfo);
        possibleActionsRuleEngine.requirePossibleAction(
                PossibleActionEnum.IMPORT_FROM_TOKEN, possibleActions);
        return importCertificate(certificateInfo.getCertificateBytes(), true, false);
    }

    /**
     * Import a cert that is found from a token by its bytes.
     * Adds audit log properties for
     * - clientId (if sign cert),
     * - cert hash and cert hash algo
     * - key usage
     * @param certificateBytes
     * @param isFromToken      whether the cert was read from a token or not
     * @return CertificateInfo
     * @throws GlobalConfOutdatedException
     * @throws KeyNotFoundException
     * @throws InvalidCertificateException          other general import failure
     * @throws CertificateAlreadyExistsException
     * @throws WrongCertificateUsageException
     * @throws AuthCertificateNotSupportedException if trying to import an auth cert from a token
     */
    private CertificateInfo importCertificate(byte[] certificateBytes, boolean isFromToken, boolean isAcme)
            throws GlobalConfOutdatedException, KeyNotFoundException, InvalidCertificateException,
                   CertificateAlreadyExistsException, WrongCertificateUsageException, CsrNotFoundException,
                   AuthCertificateNotSupportedException, ClientNotFoundException {
        globalConfService.verifyGlobalConfValidity();
        X509Certificate x509Certificate = convertToX509Certificate(certificateBytes);
        CertificateInfo certificateInfo = null;
        KeyUsageInfo keyUsageInfo = null;
        try {
            String certificateState;
            ClientId.Conf clientId = null;
            boolean isAuthCert = CertUtils.isAuthCert(x509Certificate);
            if (isAuthCert) {
                keyUsageInfo = KeyUsageInfo.AUTHENTICATION;
                securityHelper.verifyAuthority(IMPORT_AUTH_CERT);
                if (isFromToken) {
                    throw new AuthCertificateNotSupportedException("auth cert cannot be imported from a token");
                }
                certificateState = CertificateInfo.STATUS_SAVED;
            } else {
                keyUsageInfo = KeyUsageInfo.SIGNING;
                securityHelper.verifyAuthority(IMPORT_SIGN_CERT);
                String xroadInstance = globalConfProvider.getInstanceIdentifier();
                clientId = getClientIdForSigningCert(xroadInstance, x509Certificate);
                auditDataHelper.put(clientId);
                boolean clientExists = clientRepository.clientExists(clientId, true);
                if (!clientExists) {
                    throw new ClientNotFoundException("client " + clientId.toShortString() + " " + NOT_FOUND,
                            clientId.asEncodedId());
                }
                certificateState = CertificateInfo.STATUS_REGISTERED;
            }
            byte[] certBytes = x509Certificate.getEncoded();
            String hash = CryptoUtils.calculateCertHexHash(certBytes);
            auditDataHelper.putCertificateHash(hash);
            boolean activate = !isAuthCert && (!isAcme || SystemProperties.getAutomaticActivateAcmeSignCertificate());
            signerRpcClient.importCert(certBytes, certificateState, clientId, activate);
            certificateInfo = getCertificateInfo(hash);
            ClientId memberId = clientId != null ? clientId : serverConfProvider.getIdentifier().getOwner();
            if (isAcme && activate) {
                notifyAboutCertActivation(certificateInfo, memberId, keyUsageInfo);
            }
            setNextPlannedAcmeAutomaticRenewalDate(memberId, x509Certificate, keyUsageInfo, certificateInfo);
        } catch (SignerException e) {
            translateCodedExceptions(e);
        } catch (ClientNotFoundException | AccessDeniedException | AuthCertificateNotSupportedException | CodedException e) {
            throw e;
        } catch (Exception e) {
            // something went really wrong
            throw new RuntimeException("error importing certificate", e);
        }
        auditDataHelper.put(RestApiAuditProperty.KEY_USAGE, keyUsageInfo);
        return certificateInfo;
    }

    private void notifyAboutCertActivation(CertificateInfo certificateInfo, ClientId memberId, KeyUsageInfo keyUsageInfo) {
        SecurityServerId.Conf securityServerId = serverConfService.getSecurityServerId();
        if (isNotBlank(certificateInfo.getOcspVerifyBeforeActivationError())) {
            mailNotificationHelper.sendCertActivationFailureNotification(memberId.asEncodedId(),
                    certificateInfo.getCertificateDisplayName(),
                    securityServerId,
                    keyUsageInfo,
                    certificateInfo.getOcspVerifyBeforeActivationError());
        } else {
            mailNotificationHelper.sendCertActivatedNotification(memberId.asEncodedId(),
                    securityServerId,
                    certificateInfo,
                    keyUsageInfo);
        }
    }

    private void setNextPlannedAcmeAutomaticRenewalDate(ClientId memberId,
                                                        X509Certificate x509Certificate,
                                                        KeyUsageInfo keyUsageInfo,
                                                        CertificateInfo certificateInfo) throws Exception {
        X509Certificate caX509Certificate = globalConfProvider.getCaCert(memberId.getXRoadInstance(), x509Certificate);
        ApprovedCAInfo approvedCA = globalConfProvider.getApprovedCA(memberId.getXRoadInstance(), caX509Certificate);
        if (approvedCA.getAcmeServerDirectoryUrl() != null) {
            Instant nextRenewalTime = acmeService.getNextRenewalTime(memberId.asEncodedId(), approvedCA, x509Certificate, keyUsageInfo);
            signerRpcClient.setNextPlannedRenewal(certificateInfo.getId(), nextRenewalTime);
        }
    }

    /**
     * Convert cert bytes to X509Certificate, throw InvalidCertificateException if not possible
     * @throws InvalidCertificateException if bytes did not represent a valid X509Certificate
     */
    public X509Certificate convertToX509Certificate(byte[] certificateBytes) throws InvalidCertificateException {
        X509Certificate x509Certificate;
        try {
            x509Certificate = CryptoUtils.readCertificate(certificateBytes);
        } catch (Exception e) {
            throw new InvalidCertificateException("cannot convert bytes to certificate", e);
        }
        return x509Certificate;
    }

    /**
     * Activates certificate
     * @param hash
     * @throws CertificateNotFoundException if cert with given hash was not found
     * @throws AccessDeniedException        if user did not have correct permission for (de)activate
     * @throws ActionNotPossibleException   if cannot (de)activate
     */
    public void activateCertificate(String hash) throws CertificateNotFoundException,
                                                        AccessDeniedException, ActionNotPossibleException {
        changeCertificateActivation(hash, true);
    }

    /**
     * Deactivates certificate
     * @param hash
     * @throws CertificateNotFoundException if cert with given hash was not found
     * @throws AccessDeniedException        if user did not have correct permission for (de)activate
     * @throws ActionNotPossibleException   if cannot (de)activate
     */
    public void deactivateCertificate(String hash) throws CertificateNotFoundException, AccessDeniedException,
                                                          ActionNotPossibleException {
        changeCertificateActivation(hash, false);
    }

    /**
     * Deactivates or activates certificate
     */
    private void changeCertificateActivation(String hash, boolean activate) throws CertificateNotFoundException,
                                                                                   AccessDeniedException,
                                                                                   ActionNotPossibleException {

        // verify correct authority
        CertificateInfo certificateInfo = getCertificateInfo(hash);
        try {
            verifyActivateDisableAuthority(certificateInfo.getCertificateBytes());
        } catch (InvalidCertificateException e) {
            // cert from signer proxy was invalid, should not be possible
            throw new RuntimeException(e);
        }

        // verify possible actions
        EnumSet<PossibleActionEnum> possibleActions =
                getPossibleActionsForCertificateInternal(hash, certificateInfo, null, null);
        PossibleActionEnum activationAction = null;
        if (activate) {
            activationAction = PossibleActionEnum.ACTIVATE;
        } else {
            activationAction = PossibleActionEnum.DISABLE;
        }
        possibleActionsRuleEngine.requirePossibleAction(activationAction, possibleActions);

        // audit log data
        auditLogTokenKeyAndCert(hash, certificateInfo, true);

        try {
            if (activate) {
                signerRpcClient.activateCert(certificateInfo.getId());
            } else {
                signerRpcClient.deactivateCert(certificateInfo.getId());
            }
        } catch (CodedException e) {
            if (e instanceof SignerException se && se.isCausedByCertNotFound()) {
                throw new CertificateNotFoundException("Certificate with id " + certificateInfo.getId() + " "
                        + NOT_FOUND, e);
            } else {
                throw e;
            }
        } catch (Exception e) {
            var action = activate ? "activation" : "deactivation";
            throw new InternalServerErrorException("Certificate %s failed".formatted(action), e, INTERNAL_ERROR.build());
        }
    }

    /**
     * Adds audit log data for basic token, key and cert details.
     * Executes a new signer request to find out token and key details.
     * @param fullKeyDetails true: full key details are added false: only key id is added
     * @throws CertificateNotFoundException
     */
    private void auditLogTokenKeyAndCert(String hash, CertificateInfo certificateInfo, boolean fullKeyDetails)
            throws CertificateNotFoundException {
        TokenInfoAndKeyId tokenInfoAndKeyId = tokenService.getTokenAndKeyIdForCertificateHash(hash);

        TokenInfo tokenInfo = tokenInfoAndKeyId.getTokenInfo();
        KeyInfo keyInfo = tokenInfoAndKeyId.getKeyInfo();
        auditDataHelper.put(tokenInfo);
        if (fullKeyDetails) {
            auditDataHelper.put(keyInfo);
        } else {
            auditDataHelper.put(RestApiAuditProperty.KEY_ID, keyInfo.getId());
        }
        auditDataHelper.put(certificateInfo);
        auditDataHelper.put(RestApiAuditProperty.CERT_ID, certificateInfo.getId());
    }

    /**
     * Import a cert from given bytes. If importing an existing cert from a token use
     * {@link #importCertificateFromToken(String hash)}
     * @param certificateBytes
     * @return CertificateInfo
     * @throws GlobalConfOutdatedException
     * @throws ClientNotFoundException
     * @throws KeyNotFoundException
     * @throws InvalidCertificateException          other general import failure
     * @throws CertificateAlreadyExistsException
     * @throws WrongCertificateUsageException
     * @throws AuthCertificateNotSupportedException if trying to import an auth cert from a token
     */
    public CertificateInfo importCertificate(byte[] certificateBytes)
            throws InvalidCertificateException, GlobalConfOutdatedException,
                   KeyNotFoundException, CertificateAlreadyExistsException,
                   WrongCertificateUsageException, ClientNotFoundException, CsrNotFoundException,
                   AuthCertificateNotSupportedException {
        return importCertificate(certificateBytes, false, false);
    }

    /**
     * Check user authority to the given certificate
     * @param certificateBytes
     * @throws InvalidCertificateException if bytes were not valid cert
     * @throws AccessDeniedException       if no authority to activate or disable cert
     */
    private void verifyActivateDisableAuthority(byte[] certificateBytes)
            throws InvalidCertificateException, AccessDeniedException {

        X509Certificate x509Certificate = null;
        try {
            x509Certificate = convertToX509Certificate(certificateBytes);
        } catch (InvalidCertificateException e) {
            throw new InternalServerErrorException(e);
        }

        if (isValidAuthCert(x509Certificate)) {
            securityHelper.verifyAuthority("ACTIVATE_DISABLE_AUTH_CERT");
        } else if (isValidSignCert(x509Certificate)) {
            securityHelper.verifyAuthority("ACTIVATE_DISABLE_SIGN_CERT");
        } else {
            throw new InvalidCertificateException("Certificate is neither auth nor sign cert");
        }
    }

    /**
     * Check if given certificate is a valid auth cert. Any exceptions (such as missing key usage extension)
     * while processing result in a logged warning and return false
     * @param x509Certificate the certificate
     */
    public boolean isValidAuthCert(X509Certificate x509Certificate) {
        try {
            return CertUtils.isAuthCert(x509Certificate);
        } catch (Exception e) {
            log.warn("Invalid cert when checking if it is an auth cert", e);
            return false;
        }
    }

    /**
     * Check if given certificate is a valid sign cert. Any exceptions (such as missing key usage extension)
     * while processing result in a logged warning and return false
     * @param x509Certificate the certificate
     */
    public boolean isValidSignCert(X509Certificate x509Certificate) {
        try {
            return CertUtils.isSigningCert(x509Certificate);
        } catch (Exception e) {
            log.warn("Invalid cert when checking if it is a sign cert", e);
            return false;
        }
    }


    /**
     * Returns the given certificate owner's client ID.
     * @param instanceIdentifier instance identifier of the owner
     * @param cert               the certificate
     * @return certificate owner's client ID
     * @throws InvalidCertificateException if any errors occur
     */
    private ClientId.Conf getClientIdForSigningCert(String instanceIdentifier, X509Certificate cert)
            throws InvalidCertificateException {
        ClientId.Conf dummyClientId = ClientId.Conf.create(instanceIdentifier, DUMMY_MEMBER, DUMMY_MEMBER);
        SignCertificateProfileInfoParameters signCertificateProfileInfoParameters =
                new SignCertificateProfileInfoParameters(dummyClientId, DUMMY_MEMBER);
        ClientId.Conf certificateSubject;
        try {
            certificateSubject = globalConfProvider.getSubjectName(signCertificateProfileInfoParameters, cert);
        } catch (Exception e) {
            throw new InvalidCertificateException("Cannot read member identifier from signing certificate", e);
        }
        return certificateSubject;
    }

    /**
     * Verify if action can be performed on cert
     * @param action
     * @param certificateInfo
     * @param hash
     * @throws CertificateNotFoundException
     * @throws KeyNotFoundException
     * @throws ActionNotPossibleException
     */
    private void verifyCertAction(PossibleActionEnum action, CertificateInfo certificateInfo, String hash)
            throws CertificateNotFoundException, KeyNotFoundException, ActionNotPossibleException {
        TokenInfoAndKeyId tokenInfoAndKeyId = tokenService.getTokenAndKeyIdForCertificateHash(hash);
        TokenInfo tokenInfo = tokenInfoAndKeyId.getTokenInfo();
        KeyInfo keyInfo = tokenInfoAndKeyId.getKeyInfo();
        possibleActionsRuleEngine.requirePossibleCertificateAction(action, tokenInfo, keyInfo, certificateInfo);
    }

    /**
     * Send the authentication certificate registration request to central server
     * @param hash                  certificate hash
     * @param securityServerAddress IP address or DNS name of the security server
     * @throws CertificateNotFoundException
     * @throws GlobalConfOutdatedException
     * @throws InvalidCertificateException
     * @throws SignCertificateNotSupportedException
     * @throws KeyNotFoundException
     * @throws ActionNotPossibleException
     */
    public void registerAuthCert(String hash, String securityServerAddress)
            throws CertificateNotFoundException, GlobalConfOutdatedException,
                   InvalidCertificateException, SignCertificateNotSupportedException,
                   KeyNotFoundException, ActionNotPossibleException {
        CertificateInfo certificateInfo = getCertificateInfo(hash);
        auditLogTokenKeyAndCert(hash, certificateInfo, false);
        verifyAuthCert(certificateInfo);
        verifyCertAction(PossibleActionEnum.REGISTER, certificateInfo, hash);
        try {
            Integer requestId = managementRequestSenderService.sendAuthCertRegisterRequest(securityServerAddress,
                    certificateInfo.getCertificateBytes());
            auditDataHelper.put(RestApiAuditProperty.ADDRESS, securityServerAddress);
            auditDataHelper.putManagementRequestId(requestId);
            auditDataHelper.put(RestApiAuditProperty.CERT_STATUS, CertificateInfo.STATUS_REGINPROG);
            signerRpcClient.setCertStatus(certificateInfo.getId(), CertificateInfo.STATUS_REGINPROG);
        } catch (DeviationAwareRuntimeException | CodedException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException("Could not register auth cert", e, INTERNAL_ERROR.build());
        }
    }

    /**
     * Send the authentication certificate deletion request to central server and set the cert status to
     * {@link CertificateInfo#STATUS_DELINPROG}
     * @param hash           certificate hash
     * @param skipUnregister whether to skip the actual delete request and only change cert status
     * @throws SignCertificateNotSupportedException
     * @throws ActionNotPossibleException
     * @throws GlobalConfOutdatedException
     * @throws InvalidCertificateException
     * @throws KeyNotFoundException
     * @throws CertificateNotFoundException
     * @throws ManagementRequestSendingFailedException
     */
    private void unregisterAuthCertAndMarkForDeletion(String hash, boolean skipUnregister)
            throws CertificateNotFoundException, GlobalConfOutdatedException,
                   InvalidCertificateException, SignCertificateNotSupportedException, KeyNotFoundException,
                   ActionNotPossibleException, ManagementRequestSendingFailedException {
        CertificateInfo certificateInfo = getCertificateInfo(hash);
        auditLogTokenKeyAndCert(hash, certificateInfo, false);
        verifyAuthCert(certificateInfo);
        verifyCertAction(PossibleActionEnum.UNREGISTER, certificateInfo, hash);
        if (!skipUnregister) {
            Integer requestId = managementRequestSenderService.sendAuthCertDeletionRequest(
                    certificateInfo.getCertificateBytes());
            auditDataHelper.putManagementRequestId(requestId);
        }
        try {
            auditDataHelper.put(RestApiAuditProperty.CERT_STATUS, CertificateInfo.STATUS_DELINPROG);
            signerRpcClient.setCertStatus(certificateInfo.getId(), CertificateInfo.STATUS_DELINPROG);
        } catch (CodedException e) {
            throw e;
        } catch (Exception e) {
            // this means that cert was not found (which has been handled already) or some Akka error
            throw new InternalServerErrorException("Could not change auth cert status", e, INTERNAL_ERROR.build());
        }
    }

    /**
     * Send the authentication certificate deletion request to central server and set cert status to
     * {@link CertificateInfo#STATUS_DELINPROG}
     * @param hash certificate hash
     * @throws SignCertificateNotSupportedException
     * @throws ActionNotPossibleException
     * @throws GlobalConfOutdatedException
     * @throws InvalidCertificateException
     * @throws KeyNotFoundException
     * @throws CertificateNotFoundException
     * @throws ManagementRequestSendingFailedException
     */
    public void unregisterAuthCert(String hash)
            throws SignCertificateNotSupportedException, ActionNotPossibleException,
                   GlobalConfOutdatedException, InvalidCertificateException,
                   KeyNotFoundException, CertificateNotFoundException,
                   ManagementRequestSendingFailedException {
        unregisterAuthCertAndMarkForDeletion(hash, false);
    }

    /**
     * Set the authentication certificate status to {@link CertificateInfo#STATUS_DELINPROG}
     * @param hash certificate hash
     * @throws SignCertificateNotSupportedException
     * @throws ActionNotPossibleException
     * @throws GlobalConfOutdatedException
     * @throws InvalidCertificateException
     * @throws KeyNotFoundException
     * @throws CertificateNotFoundException
     */
    public void markAuthCertForDeletion(String hash)
            throws SignCertificateNotSupportedException, ActionNotPossibleException,
                   GlobalConfOutdatedException, InvalidCertificateException,
                   KeyNotFoundException, CertificateNotFoundException {

        unregisterAuthCertAndMarkForDeletion(hash, true);
    }

    private void verifyAuthCert(CertificateInfo certificateInfo)
            throws SignCertificateNotSupportedException, InvalidCertificateException {
        boolean isAuthCert;
        X509Certificate certificate;
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
    private void translateCodedExceptions(SignerException e)
            throws CertificateAlreadyExistsException, InvalidCertificateException,
                   WrongCertificateUsageException, CsrNotFoundException,
                   KeyNotFoundException {
        if (e.isCausedByDuplicateCertificate()) {
            throw new CertificateAlreadyExistsException(e);
        } else if (e.isCausedByIncorrectCertificate()) {
            throw new InvalidCertificateException(e);
        } else if (e.isCausedByCertificateWrongUsage()) {
            throw new WrongCertificateUsageException(e);
        } else if (e.isCausedByCsrNotFound()) {
            throw new CsrNotFoundException(e);
        } else if (e.isCausedByKeyNotFound()) {
            throw new KeyNotFoundException(e);
        } else {
            throw e;
        }
    }

    /**
     * Return possible actions for one cert
     * @param hash
     * @return
     * @throws CertificateNotFoundException
     */
    public EnumSet<PossibleActionEnum> getPossibleActionsForCertificate(String hash)
            throws CertificateNotFoundException {
        return getPossibleActionsForCertificateInternal(hash, null, null, null);
    }

    /**
     * Return possible actions for one csr
     * Key not found exceptions are wrapped as RuntimeExceptions
     * since them happening is considered to be internal error.
     * @throws CsrNotFoundException
     */
    public EnumSet<PossibleActionEnum> getPossibleActionsForCsr(
            String csrId) throws CsrNotFoundException {

        TokenInfoAndKeyId tokenInfoAndKeyId = tokenService.getTokenAndKeyIdForCertificateRequestId(csrId);

        TokenInfo tokenInfo = tokenInfoAndKeyId.getTokenInfo();

        return possibleActionsRuleEngine.getPossibleCsrActions(tokenInfo);
    }

    /**
     * Helper method which finds possible actions for certificate with given hash.
     * Either uses given CertificateInfo, KeyInfo and TokenInfo objects, or looks
     * them up based on cert hash if not given.
     * If TokenInfo needs to be loaded, ignores KeyInfo parameter and uses loaded TokenInfo
     * instead to determine correct KeyInfo.
     * Key not found exceptions are wrapped as RuntimeExceptions
     * since them happening is considered to be internal error.
     * @param hash            certificate hash
     * @param certificateInfo
     * @param keyInfo
     * @param tokenInfo
     * @throws CertificateNotFoundException
     */
    private EnumSet<PossibleActionEnum> getPossibleActionsForCertificateInternal(
            String hash,
            CertificateInfo certificateInfo,
            KeyInfo keyInfo,
            TokenInfo tokenInfo) throws CertificateNotFoundException {

        if (certificateInfo == null) {
            certificateInfo = getCertificateInfo(hash);
        }

        if (tokenInfo == null) {
            TokenInfoAndKeyId tokenInfoAndKeyId = tokenService.getTokenAndKeyIdForCertificateHash(hash);
            tokenInfo = tokenInfoAndKeyId.getTokenInfo();
            keyInfo = tokenInfoAndKeyId.getKeyInfo();
        }
        if (keyInfo == null) {
            String keyId = getKeyIdForCertificateHash(hash);
            keyInfo = keyService.getKey(keyId);
        }

        return possibleActionsRuleEngine.
                getPossibleCertificateActions(tokenInfo, keyInfo, certificateInfo);
    }

    /**
     * Deletes a collection of certificates
     * @throws CertificateNotFoundException if certificate with given hash was not found
     * @throws ActionNotPossibleException   if delete was not possible due to cert/key/token states
     */
    public void deleteCertificates(List<CertificateInfo> certificateInfos)
            throws CertificateNotFoundException, ActionNotPossibleException {
        List<TokenInfo> tokenInfos = tokenService.getAllTokens();
        for (CertificateInfo certificateInfo : certificateInfos) {
            deleteCertificate(certificateInfo.getId(), tokenInfos);
        }
    }

    private void deleteCertificate(String certificateId, List<TokenInfo> allTokens)
            throws CertificateNotFoundException, ActionNotPossibleException {
        // find token, key, and certificate info
        for (TokenInfo tokenInfo : allTokens) {
            for (KeyInfo keyInfo : tokenInfo.getKeyInfo()) {
                for (CertificateInfo certificateInfo : keyInfo.getCerts()) {
                    if (certificateInfo.getId().equals(certificateId)) {
                        auditDataHelper.addCertificateHash(certificateInfo);
                        deleteCertificate(certificateInfo, keyInfo, tokenInfo);
                        return;
                    }
                }
            }
        }
        throw new CertificateNotFoundException("did not find certificate with id " + certificateId + " in tokens");
    }

    /**
     * Delete certificate with given hash
     * @param hash
     * @throws CertificateNotFoundException if certificate with given hash was not found
     * @throws KeyNotFoundException         if for some reason the key linked to the cert could not
     *                                      be loaded (should not be possible)
     * @throws ActionNotPossibleException   if delete was not possible due to cert/key/token states
     */
    public void deleteCertificate(String hash) throws CertificateNotFoundException, KeyNotFoundException,
                                                      ActionNotPossibleException {
        hash = hash.toLowerCase();
        CertificateInfo certificateInfo = getCertificateInfo(hash);
        if (certificateInfo.isSavedToConfiguration()) {
            auditEventHelper.changeRequestScopedEvent(RestApiAuditEvent.DELETE_CERT_FROM_CONFIG);
        } else {
            auditEventHelper.changeRequestScopedEvent(RestApiAuditEvent.DELETE_CERT_FROM_TOKEN);
        }
        TokenInfoAndKeyId tokenInfoAndKeyId = tokenService.getTokenAndKeyIdForCertificateHash(hash);
        TokenInfo tokenInfo = tokenInfoAndKeyId.getTokenInfo();
        KeyInfo keyInfo = tokenInfoAndKeyId.getKeyInfo();
        auditDataHelper.put(tokenInfo);
        auditDataHelper.put(keyInfo);
        auditDataHelper.put(certificateInfo);

        deleteCertificate(certificateInfo, keyInfo, tokenInfo);
    }

    /**
     * Delete certificate with given hash
     * @throws CertificateNotFoundException if signer could not find the cert
     * @throws ActionNotPossibleException   if delete was not possible due to cert/key/token states
     */
    private void deleteCertificate(CertificateInfo certificateInfo, KeyInfo keyInfo, TokenInfo tokenInfo)
            throws CertificateNotFoundException, ActionNotPossibleException {
        possibleActionsRuleEngine.requirePossibleCertificateAction(
                PossibleActionEnum.DELETE, tokenInfo, keyInfo, certificateInfo);

        // audit log delete for delete cert

        if (keyInfo.isForSigning()) {
            securityHelper.verifyAuthority("DELETE_SIGN_CERT");
        } else {
            securityHelper.verifyAuthority("DELETE_AUTH_CERT");
        }
        try {
            signerRpcClient.deleteCert(certificateInfo.getId());
        } catch (CodedException e) {
            if (e instanceof SignerException se && se.isCausedByCertNotFound()) {
                throw new CertificateNotFoundException(e, CERTIFICATE_NOT_FOUND_WITH_ID.build(certificateInfo.getId()));
            } else {
                throw e;
            }
        } catch (Exception other) {
            throw new InternalServerErrorException("Deleting a CSR failed", other, INTERNAL_ERROR.build());
        }
    }

    /**
     * Return key id for a key containing a cert with given hash
     * @throws CertificateNotFoundException if no match found
     */
    public String getKeyIdForCertificateHash(String hash) throws CertificateNotFoundException {
        try {
            return signerRpcClient.getKeyIdForCertHash(hash).keyId();
        } catch (CodedException e) {
            if (e instanceof SignerException se && se.isCausedByCertNotFound()) {
                throw new CertificateNotFoundException("Certificate with hash " + hash + " not found", e);
            } else {
                throw e;
            }
        } catch (Exception e) {
            throw new InternalServerErrorException("Error getting certificate", e, INTERNAL_ERROR.build());
        }
    }

    /**
     * Deletes one csr
     * @param csrId
     * @throws KeyNotFoundException       if for some reason the key linked to the csr could not
     *                                    be loaded (should not be possible)
     * @throws CsrNotFoundException       if csr with csrId was not found
     * @throws ActionNotPossibleException if delete was not possible due to csr/key/token states
     */
    public void deleteCsr(String csrId) throws KeyNotFoundException, CsrNotFoundException,
                                               ActionNotPossibleException {

        // different audit fields for these events
        if (auditDataHelper.dataIsForEvent(RestApiAuditEvent.DELETE_ORPHANS)) {
            auditDataHelper.addListPropertyItem(RestApiAuditProperty.CERT_REQUEST_IDS, csrId);
        } else if (auditDataHelper.dataIsForEvent(RestApiAuditEvent.DELETE_CSR)) {
            auditDataHelper.put(RestApiAuditProperty.CSR_ID, csrId);
        }

        TokenInfoAndKeyId tokenInfoAndKeyId = tokenService.getTokenAndKeyIdForCertificateRequestId(csrId);
        TokenInfo tokenInfo = tokenInfoAndKeyId.getTokenInfo();
        KeyInfo keyInfo = tokenInfoAndKeyId.getKeyInfo();
        if (auditDataHelper.dataIsForEvent(RestApiAuditEvent.DELETE_CSR)) {
            auditDataHelper.put(tokenInfo);
            auditDataHelper.put(keyInfo);
        }
        CertRequestInfo certRequestInfo = getCsr(keyInfo, csrId);

        if (keyInfo.isForSigning()) {
            securityHelper.verifyAuthority("DELETE_SIGN_CERT");
        } else {
            securityHelper.verifyAuthority("DELETE_AUTH_CERT");
        }

        // check that delete is possible
        possibleActionsRuleEngine.requirePossibleCsrAction(
                PossibleActionEnum.DELETE, tokenInfo, keyInfo, certRequestInfo);

        try {
            signerRpcClient.deleteCertRequest(csrId);
        } catch (CodedException e) {
            if (e instanceof SignerException se && se.isCausedByCsrNotFound()) {
                throw new CsrNotFoundException(e);
            } else {
                throw e;
            }
        } catch (Exception other) {
            throw new InternalServerErrorException("Deleting a CSR failed", other, INTERNAL_ERROR.build());
        }
    }

    /**
     * Order certificate with the given csr from the given CA's ACME server
     */
    public void orderAcmeCertificate(String caName, String csrId, KeyUsageInfo keyUsage)
            throws CertificateAuthorityNotFoundException, CsrNotFoundException, KeyNotFoundException,
                   ActionNotPossibleException, CertificateAlreadyExistsException, ClientNotFoundException,
                   GlobalConfOutdatedException, WrongCertificateUsageException, InvalidCertificateException,
                   AuthCertificateNotSupportedException {
        auditDataHelper.put(RestApiAuditProperty.KEY_USAGE, keyUsage);
        auditDataHelper.put(RestApiAuditProperty.CERTIFICATION_SERVICE_NAME, caName);

        ApprovedCAInfo caInfo = certificateAuthorityService.getCertificateAuthorityInfo(caName);
        TokenInfoAndKeyId tokenInfoAndKeyId = tokenService.getTokenAndKeyIdForCertificateRequestId(csrId);
        KeyInfo keyInfo = tokenInfoAndKeyId.getKeyInfo();

        auditDataHelper.put(tokenInfoAndKeyId.getTokenInfo());
        auditDataHelper.put(keyInfo);

        CertRequestInfo certRequestInfo = getCsr(keyInfo, csrId);
        auditDataHelper.put(RestApiAuditProperty.SUBJECT_NAME, certRequestInfo.getSubjectName());

        SignerRpcClient.GeneratedCertRequestInfo generatedCertRequestInfo =
                regenerateCertRequest(keyInfo.getId(), csrId, CertificateRequestFormat.DER);
        if (caInfo.getAcmeServerDirectoryUrl() != null) {
            String commonName = getCommonName(certRequestInfo.getSubjectName());
            String subjectAltName = certRequestInfo.getSubjectAltName();
            if (subjectAltName != null) {
                auditDataHelper.put(RestApiAuditProperty.SUBJECT_ALT_NAME, subjectAltName);
            }
            String memberId = keyUsage == KeyUsageInfo.SIGNING
                    ? certRequestInfo.getMemberId().asEncodedId()
                    : serverConfProvider.getIdentifier().getOwner().asEncodedId();
            List<X509Certificate> chain = acmeService.orderCertificateFromACMEServer(
                    commonName,
                    subjectAltName,
                    keyUsage,
                    caInfo,
                    memberId,
                    generatedCertRequestInfo.certRequest());
            if (chain != null) {
                try {
                    importCertificate(chain.get(0).getEncoded(), false, true);
                } catch (CertificateEncodingException e) {
                    throw new InvalidCertificateException(e);
                }
            }
        }
    }

    /**
     * Finds csr with matching id from KeyInfo, or throws {@link CsrNotFoundException}
     * @throws CsrNotFoundException
     */
    private CertRequestInfo getCsr(KeyInfo keyInfo, String csrId) throws CsrNotFoundException {
        Optional<CertRequestInfo> csr = keyInfo.getCertRequests().stream()
                .filter(csrInfo -> csrInfo.getId().equals(csrId))
                .findFirst();
        if (csr.isEmpty()) {
            throw new CsrNotFoundException("csr with id " + csrId + " " + NOT_FOUND);
        }
        return csr.get();
    }

    /**
     * Cert usage info is wrong (e.g. cert is both auth and sign or neither)
     */
    public static class WrongCertificateUsageException extends InternalServerErrorException {
        public WrongCertificateUsageException(Throwable t) {
            super(t, CERTIFICATE_WRONG_USAGE.build());
        }
    }

    /**
     * Probably a rare case of when importing an auth cert from an HSM
     */
    public static class AuthCertificateNotSupportedException extends InternalServerErrorException {
        public AuthCertificateNotSupportedException(String msg) {
            super(AUTH_CERT_NOT_SUPPORTED.build(msg));
        }
    }

    /**
     * When trying to register a sign cert
     */
    public static class SignCertificateNotSupportedException extends BadRequestException {
        public SignCertificateNotSupportedException(String msg) {
            super(SIGN_CERT_NOT_SUPPORTED.build(msg));
        }
    }
}
