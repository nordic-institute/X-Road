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
package org.niis.xroad.securityserver.restapi.openapi;

import ee.ria.xroad.common.certificateprofile.CertificateProfileInfo;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.restapi.converter.ClientIdConverter;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.openapi.BadRequestException;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.niis.xroad.restapi.openapi.InternalServerErrorException;
import org.niis.xroad.restapi.openapi.ResourceNotFoundException;
import org.niis.xroad.securityserver.restapi.converter.CertificateAuthorityConverter;
import org.niis.xroad.securityserver.restapi.converter.CsrSubjectFieldDescriptionConverter;
import org.niis.xroad.securityserver.restapi.converter.KeyUsageTypeMapping;
import org.niis.xroad.securityserver.restapi.dto.ApprovedCaDto;
import org.niis.xroad.securityserver.restapi.openapi.model.CertificateAuthority;
import org.niis.xroad.securityserver.restapi.openapi.model.CsrSubjectFieldDescription;
import org.niis.xroad.securityserver.restapi.openapi.model.KeyUsageType;
import org.niis.xroad.securityserver.restapi.service.CertificateAuthorityNotFoundException;
import org.niis.xroad.securityserver.restapi.service.CertificateAuthorityService;
import org.niis.xroad.securityserver.restapi.service.CertificateProfileInstantiationException;
import org.niis.xroad.securityserver.restapi.service.ClientNotFoundException;
import org.niis.xroad.securityserver.restapi.service.KeyNotFoundException;
import org.niis.xroad.securityserver.restapi.service.KeyService;
import org.niis.xroad.securityserver.restapi.service.WrongKeyUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Collection;
import java.util.Set;

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
    private final CertificateAuthorityConverter certificateAuthorityConverter;
    private final KeyService keyService;
    private final CsrSubjectFieldDescriptionConverter subjectConverter;

    private ClientIdConverter clientIdConverter = new ClientIdConverter();

    /**
     * Currently returns partial CertificateAuthority objects that have only
     * name and authentication_only properties set.
     * Other properties will be added in another ticket (system parameters).
     * @return
     */
    @Override
    @PreAuthorize("hasAuthority('VIEW_APPROVED_CERTIFICATE_AUTHORITIES')"
            + " or (hasAuthority('GENERATE_AUTH_CERT_REQ') and "
            + " (#keyUsageType == T(org.niis.xroad.securityserver.restapi.openapi.model.KeyUsageType).AUTHENTICATION"
            + " or #keyUsageType == null))"
            + "or (hasAuthority('GENERATE_SIGN_CERT_REQ') and "
            + "#keyUsageType == T(org.niis.xroad.securityserver.restapi.openapi.model.KeyUsageType).SIGNING)")
    public ResponseEntity<Set<CertificateAuthority>> getApprovedCertificateAuthorities(KeyUsageType keyUsageType,
            Boolean includeIntermediateCas) {
        KeyUsageInfo keyUsageInfo = KeyUsageTypeMapping.map(keyUsageType).orElse(null);
        Collection<ApprovedCaDto> caDtos = null;
        try {
            caDtos = certificateAuthorityService.getCertificateAuthorities(keyUsageInfo, includeIntermediateCas);
        } catch (CertificateAuthorityService.InconsistentCaDataException e) {
            throw new InternalServerErrorException(e);
        }
        Set<CertificateAuthority> cas = certificateAuthorityConverter.convert(caDtos);
        return new ResponseEntity<>(cas, HttpStatus.OK);
    }

    @SuppressWarnings("squid:S3655") // see reason below
    @Override
    @PreAuthorize("(hasAuthority('GENERATE_AUTH_CERT_REQ') and "
            + " (#keyUsageType == T(org.niis.xroad.securityserver.restapi.openapi.model.KeyUsageType).AUTHENTICATION))"
            + " or (hasAuthority('GENERATE_SIGN_CERT_REQ') and "
            + "(#keyUsageType == T(org.niis.xroad.securityserver.restapi.openapi.model.KeyUsageType).SIGNING))")
    public ResponseEntity<Set<CsrSubjectFieldDescription>> getSubjectFieldDescriptions(
            String caName,
            KeyUsageType keyUsageType,
            String keyId,
            String encodedMemberId,
            Boolean isNewMember) {

        // squid:S3655 throwing NoSuchElementException if there is no value present is
        // fine since keyUsageInfo is mandatory parameter
        KeyUsageInfo keyUsageInfo = KeyUsageTypeMapping.map(keyUsageType).get();

        // memberId is mandatory for sign csrs
        if (keyUsageInfo == KeyUsageInfo.SIGNING) {
            if (StringUtils.isBlank(encodedMemberId)) {
                throw new BadRequestException("memberId is mandatory for sign csrs");
            }
        }

        try {
            if (!StringUtils.isBlank(keyId)) {
                // validate that key.usage matches keyUsageType
                KeyInfo keyInfo = keyService.getKey(keyId);
                if (keyInfo.getUsage() != null) {
                    if (keyInfo.getUsage() != keyUsageInfo) {
                        throw new BadRequestException("key is for different usage",
                                new ErrorDeviation("wrong_key_usage"));
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
            Set<CsrSubjectFieldDescription> converted = subjectConverter.convert(
                    profileInfo.getSubjectFields());
            return new ResponseEntity<>(converted, HttpStatus.OK);

        } catch (WrongKeyUsageException | KeyNotFoundException | ClientNotFoundException e) {
            throw new BadRequestException(e);
        } catch (CertificateAuthorityNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (CertificateProfileInstantiationException e) {
            throw new InternalServerErrorException(e);
        }
    }

}
