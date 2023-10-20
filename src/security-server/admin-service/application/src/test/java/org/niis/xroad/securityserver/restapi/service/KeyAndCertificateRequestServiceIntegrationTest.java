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
import ee.ria.xroad.common.conf.globalconf.ApprovedCAInfo;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.signer.SignerProxy;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.restapi.exceptions.DeviationAwareRuntimeException;
import org.niis.xroad.securityserver.restapi.util.TokenTestUtils;
import org.niis.xroad.signer.proto.CertificateRequestFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class KeyAndCertificateRequestServiceIntegrationTest extends AbstractServiceIntegrationTestContext {

    @Autowired
    KeyAndCertificateRequestService keyAndCertificateRequestService;

    public static final String SOFTWARE_TOKEN_ID = PossibleActionsRuleEngine.SOFTWARE_TOKEN_ID;
    public static final String OTHER_TOKEN_ID = "1";
    public static final String MOCK_CA = "mock-ca";
    Map<String, TokenInfo> tokens = new HashMap<>();

    @Before
    public void setup() throws Exception {
        TokenInfo token0 = new TokenTestUtils.TokenInfoBuilder()
                .id(SOFTWARE_TOKEN_ID)
                .type(TokenInfo.SOFTWARE_MODULE_TYPE)
                .friendlyName("mock-token0")
                .build();
        TokenInfo token1 = new TokenTestUtils.TokenInfoBuilder()
                .id(OTHER_TOKEN_ID)
                .type("mock-type")
                .friendlyName("mock-token1")
                .build();

        tokens.put(token0.getId(), token0);
        tokens.put(token1.getId(), token1);
        // mock related signer proxy methods
        when(signerProxyFacade.getTokens()).thenAnswer(i -> new ArrayList<>(tokens.values()));
        when(signerProxyFacade.getToken(any())).thenAnswer(
                invocation -> tokens.get(invocation.getArguments()[0]));
        when(signerProxyFacade.generateKey(any(), any())).thenAnswer(invocation -> {
            String tokenId = (String) invocation.getArguments()[0];
            String label = (String) invocation.getArguments()[1];
            // new keys start with usage = null
            KeyInfo keyInfo = new TokenTestUtils.KeyInfoBuilder()
                    .id(label)
                    .keyUsageInfo(null)
                    .friendlyName(label)
                    .build();
            TokenInfo token = tokens.get(tokenId);
            TokenInfo newTokenInfo = new TokenInfo(token.getMessage().toBuilder()
                    .addKeyInfo(keyInfo.getMessage())
                    .build());
            tokens.put(tokenId, newTokenInfo);
            return keyInfo;
        });
        when(signerProxyFacade.getTokenForKeyId(any())).thenAnswer(invocation -> {
            String keyId = (String) invocation.getArguments()[0];
            return getTokenWithKey(tokens, keyId);
        });
        when(signerProxyFacade.generateCertRequest(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    // keyInfo is immutable, so we need some work to replace KeyInfo with
                    // one that has correct usage
                    String keyId = (String) invocation.getArguments()[0];
                    KeyUsageInfo keyUsage = (KeyUsageInfo) invocation.getArguments()[2];
                    KeyInfo keyInfo = getKey(tokens, keyId);
                    TokenInfo tokenInfo = getTokenWithKey(tokens, keyId);
                    KeyInfo copy = new TokenTestUtils.KeyInfoBuilder()
                            .keyInfo(keyInfo)
                            .keyUsageInfo(keyUsage)
                            .build();

                    final ArrayList<KeyInfo> keyInfos = new ArrayList<>(tokenInfo.getKeyInfo());
                    keyInfos.remove(keyInfo);
                    keyInfos.add(copy);

                    TokenInfo newToken = new TokenInfo(tokenInfo.getMessage().toBuilder()
                            .clearKeyInfo()
                            .addAllKeyInfo(keyInfos.stream().map(KeyInfo::getMessage).collect(Collectors.toList()))
                            .build());
                    tokens.put(newToken.getId(), newToken);

                    return new SignerProxy.GeneratedCertRequestInfo(null, null, null, null, null);
                });
        when(globalConfFacade.getApprovedCAs(any())).thenReturn(Arrays.asList(
                new ApprovedCAInfo(MOCK_CA, false,
                        "ee.ria.xroad.common.certificateprofile.impl.FiVRKCertificateProfileInfoProvider")));
        ClientId.Conf ownerId = ClientId.Conf.create("FI", "GOV", "M1");
        SecurityServerId.Conf ownerSsId = SecurityServerId.Conf.create(ownerId, "TEST-INMEM-SS");
        when(currentSecurityServerId.getServerId()).thenReturn(ownerSsId);
    }

    private KeyInfo getKey(Map<String, TokenInfo> tokenInfos, String keyId) {
        for (TokenInfo tokenInfo : tokenInfos.values()) {
            for (KeyInfo keyInfo : tokenInfo.getKeyInfo()) {
                if (keyInfo.getId().equals(keyId)) {
                    return keyInfo;
                }
            }
        }
        return null;
    }

    private TokenInfo getTokenWithKey(Map<String, TokenInfo> tokenInfos, String keyId) {
        for (TokenInfo tokenInfo : tokenInfos.values()) {
            for (KeyInfo keyInfo : tokenInfo.getKeyInfo()) {
                if (keyInfo.getId().equals(keyId)) {
                    return tokenInfo;
                }
            }
        }
        return null;
    }

    @Test
    @WithMockUser(authorities = {"DELETE_KEY", "DELETE_SIGN_KEY", "DELETE_AUTH_KEY"})
    public void addKeyAndCertSuccess() throws Exception {
        HashMap<String, String> dnParams = createCsrDnParams();
        KeyAndCertificateRequestService.KeyAndCertRequestInfo info = keyAndCertificateRequestService
                .addKeyAndCertRequest(SOFTWARE_TOKEN_ID, "keylabel",
                        ClientId.Conf.create("FI", "GOV", "M1"),
                        KeyUsageInfo.SIGNING, MOCK_CA, dnParams,
                        CertificateRequestFormat.PEM);
        verify(signerProxyFacade, times(1))
                .generateKey(SOFTWARE_TOKEN_ID, "keylabel");
        verify(signerProxyFacade, times(1))
                .generateCertRequest(any(), any(), any(), any(), any());
    }

    private HashMap<String, String> createCsrDnParams() {
        HashMap<String, String> dnParams = new HashMap<>();
        dnParams.put("C", "FI");
        dnParams.put("O", "foobar-o");
        dnParams.put("serialNumber", "FI/ss1/GOV");
        dnParams.put("CN", "foobar-cn");
        return dnParams;
    }

    @Test
    @WithMockUser(authorities = {"DELETE_KEY", "DELETE_SIGN_KEY", "DELETE_AUTH_KEY"})
    public void canAddAuthKeyToSoftToken() throws Exception {
        HashMap<String, String> dnParams = createCsrDnParams();
        KeyAndCertificateRequestService.KeyAndCertRequestInfo info = keyAndCertificateRequestService
                .addKeyAndCertRequest(SOFTWARE_TOKEN_ID, "keylabel",
                        null,
                        KeyUsageInfo.AUTHENTICATION, MOCK_CA, dnParams,
                        CertificateRequestFormat.PEM);
        assertNotNull(info);
    }

    @Test(expected = ActionNotPossibleException.class)
    @WithMockUser(authorities = {"DELETE_KEY", "DELETE_SIGN_KEY", "DELETE_AUTH_KEY"})
    public void cannotAddAuthKeyToNonSoftToken() throws Exception {
        HashMap<String, String> dnParams = createCsrDnParams();
        KeyAndCertificateRequestService.KeyAndCertRequestInfo info = keyAndCertificateRequestService
                .addKeyAndCertRequest(OTHER_TOKEN_ID, "keylabel",
                        null,
                        KeyUsageInfo.AUTHENTICATION, MOCK_CA, dnParams,
                        CertificateRequestFormat.PEM);
    }

    @Test
    @WithMockUser(authorities = {"DELETE_KEY", "DELETE_SIGN_KEY", "DELETE_AUTH_KEY"})
    public void csrGenerateFailureRollsBackKeyCreate() throws Exception {
        HashMap<String, String> dnParams = createCsrDnParams();
        try {
            ClientId.Conf notFoundClient = ClientId.Conf.create("not-found", "GOV", "M1");
            keyAndCertificateRequestService
                    .addKeyAndCertRequest(SOFTWARE_TOKEN_ID, "keylabel",
                            notFoundClient,
                            KeyUsageInfo.SIGNING, MOCK_CA, dnParams,
                            CertificateRequestFormat.PEM);
            fail("should throw exception");
        } catch (ClientNotFoundException expected) {
            // our mock sets key id = label
            verify(signerProxyFacade, times(1))
                    .deleteKey("keylabel", true);
            verify(signerProxyFacade, times(1))
                    .deleteKey("keylabel", false);
        }
    }

    @Test
    @WithMockUser(authorities = {"DELETE_KEY", "DELETE_SIGN_KEY", "DELETE_AUTH_KEY"})
    public void failedRollback() throws Exception {
        HashMap<String, String> dnParams = createCsrDnParams();
        doThrow(new CodedException(TokenService.KEY_NOT_FOUND_FAULT_CODE))
                .when(signerProxyFacade).getTokenForKeyId(any());
        try {
            ClientId.Conf notFoundClient = ClientId.Conf.create("not-found", "GOV", "M1");
            keyAndCertificateRequestService
                    .addKeyAndCertRequest(SOFTWARE_TOKEN_ID, "keylabel",
                            notFoundClient,
                            KeyUsageInfo.SIGNING, MOCK_CA, dnParams,
                            CertificateRequestFormat.PEM);
            fail("should throw exception");
        } catch (DeviationAwareRuntimeException expected) {
            // delete key -attempt will not reach signerProxyFacade
        }
    }

}
