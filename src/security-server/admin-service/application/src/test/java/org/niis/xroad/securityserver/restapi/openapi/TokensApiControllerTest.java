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

import ee.ria.xroad.common.CodedException;

import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.common.exception.ConflictException;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.globalconf.model.ApprovedCAInfo;
import org.niis.xroad.globalconf.model.CsrFormat;
import org.niis.xroad.securityserver.restapi.openapi.model.CsrFormatDto;
import org.niis.xroad.securityserver.restapi.openapi.model.CsrGenerateDto;
import org.niis.xroad.securityserver.restapi.openapi.model.KeyDto;
import org.niis.xroad.securityserver.restapi.openapi.model.KeyLabelDto;
import org.niis.xroad.securityserver.restapi.openapi.model.KeyLabelWithCsrGenerateDto;
import org.niis.xroad.securityserver.restapi.openapi.model.KeyUsageTypeDto;
import org.niis.xroad.securityserver.restapi.openapi.model.KeyWithCertificateSigningRequestIdDto;
import org.niis.xroad.securityserver.restapi.openapi.model.TokenDto;
import org.niis.xroad.securityserver.restapi.openapi.model.TokenStatusDto;
import org.niis.xroad.securityserver.restapi.service.CertificateAuthorityService;
import org.niis.xroad.securityserver.restapi.service.KeyAndCertificateRequestService;
import org.niis.xroad.securityserver.restapi.service.TokenNotFoundException;
import org.niis.xroad.securityserver.restapi.util.TestUtils;
import org.niis.xroad.securityserver.restapi.util.TokenTestUtils;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.proto.CertificateRequestFormat;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static ee.ria.xroad.common.ErrorCodes.SIGNER_X;
import static ee.ria.xroad.common.ErrorCodes.X_TOKEN_NOT_ACTIVE;
import static ee.ria.xroad.common.ErrorCodes.X_TOKEN_NOT_FOUND;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * test tokens api
 */
public class TokensApiControllerTest extends AbstractApiControllerTestContext {

    @Autowired
    TokensApiController tokensApiController;

    private static final String TOKEN_NOT_FOUND_TOKEN_ID = "token-404";
    private static final String GOOD_TOKEN_ID = "token-which-exists";
    private static final String KEY_LABEL = "key-label";

    public static final String NOT_ACTIVE_TOKEN_ID = "token-not-active";
    public static final String NOT_ACTIVE_TOKEN_KEY_ID = "token-not-active-key";

    private List<TokenInfo> allTokens;

    @MockitoBean
    private KeyAndCertificateRequestService keyAndCertificateRequestService;

    @Autowired
    private CertificateAuthorityService certificateAuthorityService;

    @Before
    public void setUp() throws Exception {
        KeyInfo keyInfo = new TokenTestUtils.KeyInfoBuilder().build();
        TokenInfo activeTokenInfo = new TokenTestUtils.TokenInfoBuilder()
                .id(GOOD_TOKEN_ID)
                .key(keyInfo)
                .build();
        KeyInfo inactiveKeyInfo = new TokenTestUtils.KeyInfoBuilder()
                .id(NOT_ACTIVE_TOKEN_KEY_ID)
                .build();
        TokenInfo inactiveTokenInfo = new TokenTestUtils.TokenInfoBuilder()
                .id(NOT_ACTIVE_TOKEN_ID)
                .active(false)
                .key(inactiveKeyInfo)
                .build();
        allTokens = Arrays.asList(activeTokenInfo, inactiveTokenInfo);

        doReturn(allTokens).when(tokenService).getAllTokens();

        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            String tokenId = (String) args[0];
            if (GOOD_TOKEN_ID.equals(tokenId)) {
                return activeTokenInfo;
            } else if (NOT_ACTIVE_TOKEN_ID.equals(tokenId)) {
                return inactiveTokenInfo;
            } else {
                throw new TokenNotFoundException(new RuntimeException());
            }
        }).when(tokenService).getToken(any());

        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            String keyId = (String) args[0];
            if (keyInfo.getId().equals(keyId)) {
                return activeTokenInfo;
            } else if (inactiveKeyInfo.getId().equals(keyId)) {
                return inactiveTokenInfo;
            } else {
                throw new TokenNotFoundException(new RuntimeException());
            }
        }).when(tokenService).getTokenForKeyId(any());

        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            String tokenId = (String) args[0];
            String keyLabel = (String) args[1];
            if (GOOD_TOKEN_ID.equals(tokenId)) {
                ReflectionTestUtils.setField(keyInfo.getMessage(), "label_", keyLabel);
                return keyInfo;
            } else if (NOT_ACTIVE_TOKEN_ID.equals(tokenId)) {
                throw new CodedException.Fault(SIGNER_X + "." + X_TOKEN_NOT_ACTIVE, null);
            } else if (TOKEN_NOT_FOUND_TOKEN_ID.equals(tokenId)) {
                throw new CodedException.Fault(SIGNER_X + "." + X_TOKEN_NOT_FOUND, null);
            }
            throw new RuntimeException("given tokenId not supported in mocked method SignerProxyFacade#generateKey");
        }).when(signerRpcClient).generateKey(any(), any(), any());
    }

    @Test
    @WithMockUser(authorities = {"VIEW_KEYS"})
    public void getTokens() {
        ResponseEntity<Set<TokenDto>> response = tokensApiController.getTokens();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Set<TokenDto> tokens = response.getBody();
        assertEquals(allTokens.size(), tokens.size());
        TokenDto token = tokens.iterator().next();
        assertEquals(TokenStatusDto.OK, token.getStatus());
        assertEquals("friendly-name", token.getName());
    }

    @Test
    @WithMockUser(authorities = {"VIEW_KEYS"})
    public void getToken() {
        try {
            tokensApiController.getToken(TOKEN_NOT_FOUND_TOKEN_ID);
            fail("should have thrown exception");
        } catch (NotFoundException expected) {
        }

        ResponseEntity<TokenDto> response = tokensApiController.getToken(GOOD_TOKEN_ID);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(GOOD_TOKEN_ID, response.getBody().getId());
    }

    @Test
    @WithMockUser(authorities = {"GENERATE_KEY"})
    public void addKey() {
        ResponseEntity<KeyDto> response = tokensApiController.addKey(GOOD_TOKEN_ID, new KeyLabelDto().label(KEY_LABEL));
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        KeyDto key = response.getBody();
        assertEquals(KEY_LABEL, key.getLabel());
        try {
            tokensApiController.addKey(TOKEN_NOT_FOUND_TOKEN_ID, new KeyLabelDto().label(KEY_LABEL));
            fail("should have thrown exception");
        } catch (NotFoundException expected) {
        }

        try {
            tokensApiController.addKey(NOT_ACTIVE_TOKEN_ID, new KeyLabelDto().label(KEY_LABEL));
            fail("should have thrown exception");
        } catch (ConflictException expected) {
        }
    }

    @Test
    @WithMockUser(authorities = {"GENERATE_KEY", "GENERATE_AUTH_CERT_REQ"})
    public void addKeyAndCsrSucceedsWhenCsrFormatMatchesCaDefault() {
        String caName = "test-ca";
        mockCa(caName, CsrFormat.PEM);
        KeyAndCertificateRequestService.KeyAndCertRequestInfo info = mockKeyAndCertRequestResponse();
        when(keyAndCertificateRequestService.addKeyAndCertRequest(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(info);

        ResponseEntity<KeyWithCertificateSigningRequestIdDto> response = tokensApiController.addKeyAndCsr(
                GOOD_TOKEN_ID,
                createKeyLabelWithCsrGenerateDto(caName, CsrFormatDto.PEM));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(info.getCertReqId(), response.getBody().getCsrId());
        assertNotNull(response.getBody().getKey());
        verify(certificateAuthorityService).validateCsrFormat(caName, CertificateRequestFormat.PEM);
        verify(keyAndCertificateRequestService).addKeyAndCertRequest(any(), any(), any(), any(), any(), any(), any(), any());
    }


    private void mockCa(String caName, CsrFormat csrFormat) {
        ApprovedCAInfo approvedCAInfo = new ApprovedCAInfo(caName, false, "profile", csrFormat,
                null, null, null, null);
        when(certificateAuthorityService.getCertificateAuthorityInfo(caName)).thenReturn(approvedCAInfo);
    }

    private KeyAndCertificateRequestService.KeyAndCertRequestInfo mockKeyAndCertRequestResponse() {
        KeyInfo keyInfo = new TokenTestUtils.KeyInfoBuilder().build();
        return new KeyAndCertificateRequestService.KeyAndCertRequestInfo(
                keyInfo,
                "csr-id",
                new byte[]{0x01},
                org.niis.xroad.signer.proto.CertificateRequestFormat.PEM,
                TestUtils.getM1Ss1ClientId(),
                KeyUsageInfo.AUTHENTICATION);
    }

    private KeyLabelWithCsrGenerateDto createKeyLabelWithCsrGenerateDto(String caName, CsrFormatDto csrFormatDto) {
        CsrGenerateDto csrGenerateDto = new CsrGenerateDto()
                .caName(caName)
                .csrFormat(csrFormatDto)
                .keyUsageType(KeyUsageTypeDto.AUTHENTICATION)
                .subjectFieldValues(new HashMap<>())
                .memberId(TestUtils.CLIENT_ID_SS1)
                .acmeOrder(false);
        return new KeyLabelWithCsrGenerateDto()
                .keyLabel(KEY_LABEL)
                .csrGenerateRequest(csrGenerateDto);
    }
}
