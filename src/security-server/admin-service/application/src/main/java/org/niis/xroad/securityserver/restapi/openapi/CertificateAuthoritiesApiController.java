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
package org.niis.xroad.securityserver.restapi.openapi;

import ee.ria.xroad.common.certificateprofile.CertificateProfileInfo;
import ee.ria.xroad.common.identifier.ClientId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.common.exception.InternalServerErrorException;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.config.audit.RestApiAuditEvent;
import org.niis.xroad.restapi.converter.ClientIdConverter;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.niis.xroad.securityserver.restapi.converter.CertificateAuthorityConverter;
import org.niis.xroad.securityserver.restapi.converter.CsrSubjectFieldDescriptionConverter;
import org.niis.xroad.securityserver.restapi.converter.KeyUsageTypeMapping;
import org.niis.xroad.securityserver.restapi.dto.ApprovedCaDto;
import org.niis.xroad.securityserver.restapi.openapi.model.AcmeEabCredentialsStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.AcmeOrderDto;
import org.niis.xroad.securityserver.restapi.openapi.model.CertificateAuthorityDto;
import org.niis.xroad.securityserver.restapi.openapi.model.CsrSubjectFieldDescriptionDto;
import org.niis.xroad.securityserver.restapi.openapi.model.KeyUsageTypeDto;
import org.niis.xroad.securityserver.restapi.service.CertificateAuthorityNotFoundException;
import org.niis.xroad.securityserver.restapi.service.CertificateAuthorityService;
import org.niis.xroad.securityserver.restapi.service.ClientNotFoundException;
import org.niis.xroad.securityserver.restapi.service.InvalidCertificateException;
import org.niis.xroad.securityserver.restapi.service.KeyNotFoundException;
import org.niis.xroad.securityserver.restapi.service.KeyService;
import org.niis.xroad.securityserver.restapi.service.TokenCertificateService;
import org.niis.xroad.securityserver.restapi.service.WrongKeyUsageException;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Collection;
import java.util.Set;

import static org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage.MEMBER_ID_REQUIRED_FOR_SIGN_CSR;

/**
 * certificate authorities api controller
 */
@Controller
@RequestMapping(ControllerUtil.API_V1_PREFIX)
@Slf4j
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class CertificateAuthoritiesApiController implements CertificateAuthoritiesApi {

    private final CertificateAuthorityService certificateAuthorityService;
    private final TokenCertificateService tokenCertificateService;
    private final CertificateAuthorityConverter certificateAuthorityConverter;
    private final KeyService keyService;
    private final CsrSubjectFieldDescriptionConverter subjectConverter;

    private ClientIdConverter clientIdConverter = new ClientIdConverter();

    /**
     * Currently returns partial CertificateAuthorityDto objects that have only
     * name and authentication_only properties set.
     * Other properties will be added in another ticket (system parameters).
     * @return
     */
    @Override
    @PreAuthorize("hasAuthority('VIEW_APPROVED_CERTIFICATE_AUTHORITIES')"
            + " or (hasAuthority('GENERATE_AUTH_CERT_REQ') and "
            + " (#keyUsageTypeDto == T(org.niis.xroad.securityserver.restapi.openapi.model.KeyUsageTypeDto).AUTHENTICATION"
            + " or #keyUsageTypeDto == null))"
            + "or (hasAuthority('GENERATE_SIGN_CERT_REQ') and "
            + "#keyUsageTypeDto == T(org.niis.xroad.securityserver.restapi.openapi.model.KeyUsageTypeDto).SIGNING)")
    public ResponseEntity<Set<CertificateAuthorityDto>> getApprovedCertificateAuthorities(KeyUsageTypeDto keyUsageTypeDto,
                                                                                          Boolean includeIntermediateCas) {
        KeyUsageInfo keyUsageInfo = KeyUsageTypeMapping.map(keyUsageTypeDto).orElse(null);
        Collection<ApprovedCaDto> caDtos = certificateAuthorityService.getCertificateAuthorities(keyUsageInfo, includeIntermediateCas);

        Set<CertificateAuthorityDto> cas = certificateAuthorityConverter.convert(caDtos);
        return new ResponseEntity<>(cas, HttpStatus.OK);
    }

    @SuppressWarnings("squid:S3655") // see reason below
    @Override
    @PreAuthorize("(hasAuthority('GENERATE_AUTH_CERT_REQ') and "
            + " (#keyUsageTypeDto == T(org.niis.xroad.securityserver.restapi.openapi.model.KeyUsageTypeDto).AUTHENTICATION))"
            + " or (hasAuthority('GENERATE_SIGN_CERT_REQ') and "
            + "(#keyUsageTypeDto == T(org.niis.xroad.securityserver.restapi.openapi.model.KeyUsageTypeDto).SIGNING))")
    public ResponseEntity<Set<CsrSubjectFieldDescriptionDto>> getSubjectFieldDescriptions(
            String caName,
            KeyUsageTypeDto keyUsageTypeDto,
            String keyId,
            String encodedMemberId,
            Boolean isNewMember) {

        // squid:S3655 throwing NoSuchElementException if there is no value present is
        // fine since keyUsageInfo is mandatory parameter
        KeyUsageInfo keyUsageInfo = KeyUsageTypeMapping.map(keyUsageTypeDto).get();

        // memberId is mandatory for sign csrs
        if (keyUsageInfo == KeyUsageInfo.SIGNING && StringUtils.isBlank(encodedMemberId)) {
            throw new BadRequestException(MEMBER_ID_REQUIRED_FOR_SIGN_CSR.build());
        }

        try {
            if (!StringUtils.isBlank(keyId)) {
                // validate that key.usage matches keyUsageType
                KeyInfo keyInfo = keyService.getKey(keyId);
                if (keyInfo.getUsage() != null) {
                    if (keyInfo.getUsage() != keyUsageInfo) {
                        throw new WrongKeyUsageException("key is for different usage");
                    }
                }
            }

            ClientId memberId = null;
            if (!StringUtils.isBlank(encodedMemberId)) {
                memberId = clientIdConverter.convertId(encodedMemberId);
            }

            CertificateProfileInfo profileInfo;
            profileInfo = certificateAuthorityService.getCertificateProfile(
                    caName, keyUsageInfo, memberId, isNewMember);
            Set<CsrSubjectFieldDescriptionDto> converted = subjectConverter.convert(
                    profileInfo.getSubjectFields());
            return new ResponseEntity<>(converted, HttpStatus.OK);

        } catch (KeyNotFoundException | ClientNotFoundException e) {
            throw new BadRequestException(e);
        }
    }

    @Override
    @PreAuthorize("(hasAuthority('IMPORT_AUTH_CERT') and "
            + " (#keyUsageTypeDto == T(org.niis.xroad.securityserver.restapi.openapi.model.KeyUsageTypeDto).AUTHENTICATION))"
            + " or (hasAuthority('IMPORT_SIGN_CERT') and "
            + "(#keyUsageTypeDto == T(org.niis.xroad.securityserver.restapi.openapi.model.KeyUsageTypeDto).SIGNING))")
    public ResponseEntity<AcmeEabCredentialsStatusDto> hasAcmeExternalAccountBindingCredentials(String caName,
                                                                                                KeyUsageTypeDto keyUsageTypeDto,
                                                                                                String memberId) {

        final var isAcmeEabRequired = certificateAuthorityService.isAcmeExternalAccountBindingRequired(caName);
        final var hasAcmeEabCredentials = certificateAuthorityService.hasAcmeExternalAccountBindingCredentials(caName, memberId);
        return new ResponseEntity<>(new AcmeEabCredentialsStatusDto(isAcmeEabRequired, hasAcmeEabCredentials), HttpStatus.OK);
    }

    @Override
    @PreAuthorize("(hasAuthority('IMPORT_AUTH_CERT') and "
            + " (#acmeOrderDto.keyUsageType == T(org.niis.xroad.securityserver.restapi.openapi.model.KeyUsageTypeDto).AUTHENTICATION))"
            + " or (hasAuthority('IMPORT_SIGN_CERT') and "
            + "(#acmeOrderDto.keyUsageType == T(org.niis.xroad.securityserver.restapi.openapi.model.KeyUsageTypeDto).SIGNING))")
    @AuditEventMethod(event = RestApiAuditEvent.ACME_ORDER_CERTIFICATE)
    public ResponseEntity<Void> orderAcmeCertificate(String caName, AcmeOrderDto acmeOrderDto) {
        KeyUsageInfo keyUsageInfo = KeyUsageTypeMapping.map(acmeOrderDto.getKeyUsageType()).orElseThrow();
        try {
            tokenCertificateService.orderAcmeCertificate(caName, acmeOrderDto.getCsrId(), keyUsageInfo);
        } catch (ClientNotFoundException | CertificateAuthorityNotFoundException | KeyNotFoundException e) {
            throw new BadRequestException(e);
        } catch (TokenCertificateService.WrongCertificateUsageException | InvalidCertificateException e) {
            throw new InternalServerErrorException(e);
        }
        return ResponseEntity.noContent().build();
    }

}
