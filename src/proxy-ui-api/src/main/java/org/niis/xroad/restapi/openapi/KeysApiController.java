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
package org.niis.xroad.restapi.openapi;

import ee.ria.xroad.common.certificateprofile.CertificateProfileInfo;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.message.GenerateCertRequest;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.converter.ClientConverter;
import org.niis.xroad.restapi.converter.CsrFormatMapping;
import org.niis.xroad.restapi.converter.DistinguishedNameFieldDescriptionConverter;
import org.niis.xroad.restapi.converter.KeyConverter;
import org.niis.xroad.restapi.converter.KeyUsageTypeMapping;
import org.niis.xroad.restapi.openapi.model.CsrGenerate;
import org.niis.xroad.restapi.openapi.model.DistinguishedNameFieldDescription;
import org.niis.xroad.restapi.openapi.model.Key;
import org.niis.xroad.restapi.openapi.model.KeyName;
import org.niis.xroad.restapi.openapi.model.KeyUsageType;
import org.niis.xroad.restapi.service.CertificateAuthorityService;
import org.niis.xroad.restapi.service.ClientNotFoundException;
import org.niis.xroad.restapi.service.KeyService;
import org.niis.xroad.restapi.service.ServerConfService;
import org.niis.xroad.restapi.service.TokenCertificateService;
import org.niis.xroad.restapi.service.WrongKeyUsageException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * keys controller
 */
@Controller
@RequestMapping("/api")
@Slf4j
@PreAuthorize("denyAll")
public class KeysApiController implements KeysApi {

    private final KeyService keyService;
    private final KeyConverter keyConverter;
    private final CertificateAuthorityService certificateAuthorityService;
    private final ClientConverter clientConverter;
    private final DistinguishedNameFieldDescriptionConverter dnConverter;
    private final TokenCertificateService tokenCertificateService;
    private final ServerConfService serverConfService;
    private final CsrFilenameCreator csrFilenameCreator;


    /**
     * KeysApiController constructor
     * @param keyConverter
     * @param keyService
     * @param certificateAuthorityService
     * @param dnConverter
     * @param clientConverter
     * @param tokenCertificateService
     * @param csrFilenameCreator
     * @param serverConfService
     */
    @Autowired
    public KeysApiController(KeyService keyService,
            KeyConverter keyConverter,
            CertificateAuthorityService certificateAuthorityService,
            ClientConverter clientConverter,
            DistinguishedNameFieldDescriptionConverter dnConverter,
            TokenCertificateService tokenCertificateService,
            ServerConfService serverConfService,
            CsrFilenameCreator csrFilenameCreator) {
        this.keyService = keyService;
        this.keyConverter = keyConverter;
        this.certificateAuthorityService = certificateAuthorityService;
        this.clientConverter = clientConverter;
        this.dnConverter = dnConverter;
        this.tokenCertificateService = tokenCertificateService;
        this.serverConfService = serverConfService;
        this.csrFilenameCreator = csrFilenameCreator;
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_KEYS')")
    public ResponseEntity<Key> getKey(String keyId) {
        Key key = getKeyFromService(keyId);
        return new ResponseEntity<>(key, HttpStatus.OK);
    }

    private Key getKeyFromService(String keyId) {
        try {
            KeyInfo keyInfo = keyService.getKey(keyId);
            return keyConverter.convert(keyInfo);
        } catch (KeyService.KeyNotFoundException e) {
            throw new ResourceNotFoundException(e);
        }
    }

    @Override
    @PreAuthorize("hasAuthority('EDIT_KEYTABLE_FRIENDLY_NAMES')")
    public ResponseEntity<Key> updateKey(String id, KeyName keyName) {
        KeyInfo keyInfo = null;
        try {
            keyInfo = keyService.updateKeyFriendlyName(id, keyName.getName());
        } catch (KeyService.KeyNotFoundException e) {
            throw new ResourceNotFoundException(e);
        }
        Key key = keyConverter.convert(keyInfo);
        return new ResponseEntity<>(key, HttpStatus.OK);
    }

    @Override
    @PreAuthorize("(hasAuthority('GENERATE_AUTH_CERT_REQ') and "
            + " (#keyUsageType == T(org.niis.xroad.restapi.openapi.model.KeyUsageType).AUTHENTICATION"
            + " or #keyUsageType == null))"
            + "or (hasAuthority('GENERATE_SIGN_CERT_REQ') and "
            + "#keyUsageType == T(org.niis.xroad.restapi.openapi.model.KeyUsageType).SIGNING)")
    public ResponseEntity<List<DistinguishedNameFieldDescription>> getCsrDnFieldDescriptions(
            String keyId,
            KeyUsageType keyUsageType,
            String caName,
            String encodedMemberId) {

        KeyUsageInfo keyUsageInfo = KeyUsageTypeMapping.map(keyUsageType).get();
        try {
            KeyInfo keyInfo = keyService.getKey(keyId);
            if (keyInfo.getUsage() != null) {
                if (keyInfo.getUsage() != keyUsageInfo) {
                    throw new ResourceNotFoundException("key is for different usage");
                }
            }
            ClientId memberId = clientConverter.convertId(encodedMemberId);

            CertificateProfileInfo profileInfo;
            profileInfo = certificateAuthorityService.getCertificateProfile(
                    caName, keyUsageInfo, memberId);
            List<DistinguishedNameFieldDescription> converted = dnConverter.convert(
                    profileInfo.getSubjectFields());
            return new ResponseEntity<>(converted, HttpStatus.OK);

        } catch (WrongKeyUsageException e) {
            throw new ResourceNotFoundException(e);
        } catch (KeyService.KeyNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (CertificateAuthorityService.CertificateAuthorityNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (ClientNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (CertificateAuthorityService.CertificateProfileInstantiationException e) {
            throw new InternalServerErrorException(e);
        }
    }

    @Override
    @PreAuthorize("(hasAuthority('GENERATE_AUTH_CERT_REQ') and "
            + "#csrGenerate.keyUsageType == T(org.niis.xroad.restapi.openapi.model.KeyUsageType).AUTHENTICATION)"
            + " or (hasAuthority('GENERATE_SIGN_CERT_REQ') and "
            + "#csrGenerate.keyUsageType == T(org.niis.xroad.restapi.openapi.model.KeyUsageType).SIGNING)")
    public ResponseEntity<Resource> generateCsr(String keyId, CsrGenerate csrGenerate) {
        KeyUsageInfo keyUsageInfo = KeyUsageTypeMapping.map(csrGenerate.getKeyUsageType()).get();
        ClientId memberId = null;
        if (KeyUsageInfo.SIGNING == keyUsageInfo) {
            // memberId not used for authentication csrs
            memberId = clientConverter.convertId(csrGenerate.getMemberId());
        }
        GenerateCertRequest.RequestFormat csrFormat = CsrFormatMapping.map(csrGenerate.getCsrFormat()).get();

        byte[] csr = null;
        try {
            csr = tokenCertificateService.generateCertRequest(keyId,
                    memberId,
                    keyUsageInfo,
                    csrGenerate.getCaName(),
                    csrGenerate.getDnFieldValues(),
                    csrFormat);
        } catch (Exception e) {
            e.printStackTrace();
            // TO DO: proper exception handling
            throw new RuntimeException("not handled yet", e);
        }

        String filename = csrFilenameCreator.createCsrFilename(keyUsageInfo, csrFormat, memberId,
                serverConfService.getSecurityServerId());
        ContentDisposition contentDisposition = ContentDisposition.builder("attachment")
                .filename(filename)
                .build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(contentDisposition);

        Resource resource = new ByteArrayResource(csr);
        return new ResponseEntity<>(resource, headers, HttpStatus.OK);
    }

}
