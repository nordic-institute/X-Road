package org.niis.xroad.restapi.service;

import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.util.TokenTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;
import static org.niis.xroad.restapi.openapi.TokensApiControllerTest.NOT_ACTIVE_TOKEN_ID;
import static org.niis.xroad.restapi.openapi.TokensApiControllerTest.NOT_ACTIVE_TOKEN_KEY_ID;
import static org.niis.xroad.restapi.service.PossibleActionsRuleEngine.SOFTWARE_TOKEN_ID;
import static org.niis.xroad.restapi.service.TokenServiceTest.GOOD_TOKEN_ID;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureTestDatabase
@Slf4j
@Transactional
@WithMockUser
public class InitializationServiceTest {
    @Autowired
    private InitializationService initializationService;

    @MockBean
    private TokenService tokenService;

    private List<TokenInfo> allTokens;

    @Before
    public void setup() {
        KeyInfo keyInfo = new TokenTestUtils.KeyInfoBuilder().build();
        TokenInfo activeTokenInfo = new TokenTestUtils.TokenInfoBuilder()
                .id(SOFTWARE_TOKEN_ID)
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
        allTokens = Arrays.asList(new TokenInfo[] {activeTokenInfo, inactiveTokenInfo});

        when(tokenService.getAllTokens()).thenReturn(allTokens);
    }

    @Test
    public void isSecurityServerInitialized() {
        initializationService.isSecurityServerInitialized();
    }
}
