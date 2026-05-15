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
package org.niis.xroad.securityserver.restapi.service;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.niis.xroad.confclient.rpc.ConfClientRpcClient;
import org.niis.xroad.restapi.config.UserAuthenticationConfig;
import org.niis.xroad.restapi.config.UserAuthenticationConfig.AuthenticationProviderType;
import org.niis.xroad.restapi.domain.AdminUser;
import org.niis.xroad.restapi.domain.Role;
import org.niis.xroad.restapi.service.AdminUserService;
import org.niis.xroad.securityserver.restapi.repository.ServerConfRepository;
import org.niis.xroad.securityserver.restapi.service.InitialAdminUserService.InitialAdminUserNotAllowedException;
import org.niis.xroad.securityserver.restapi.util.TokenTestUtils;
import org.niis.xroad.serverconf.impl.entity.ClientEntity;
import org.niis.xroad.serverconf.impl.entity.ServerConfEntity;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.protocol.dto.TokenStatusInfo;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class InitialAdminUserServiceTest {

    private static final String USERNAME = "admin";
    private static final char[] PASSWORD = "TopSecret123!".toCharArray();
    private static final byte[] ANCHOR_BYTES = new byte[]{1, 2, 3};
    private static final String SOFTWARE_TOKEN_ID = PossibleActionsRuleEngine.SOFTWARE_TOKEN_ID;

    private static final Set<Role> EXPECTED_ROLES = EnumSet.of(
            Role.XROAD_SECURITY_OFFICER,
            Role.XROAD_REGISTRATION_OFFICER,
            Role.XROAD_SERVICE_ADMINISTRATOR,
            Role.XROAD_SYSTEM_ADMINISTRATOR,
            Role.XROAD_SECURITYSERVER_OBSERVER);

    @Mock
    private UserAuthenticationConfig userAuthenticationConfig;
    @Mock
    private AdminUserService adminUserService;
    @Mock
    private ServerConfRepository serverConfRepository;
    @Mock
    private ConfClientRpcClient confClientRpcClient;
    @Mock
    private SignerRpcClient signerRpcClient;

    private InitialAdminUserService service;

    @Before
    public void setUp() {
        service = new InitialAdminUserService(userAuthenticationConfig, adminUserService,
                serverConfRepository, confClientRpcClient, signerRpcClient);
    }

    @Test
    public void requiredWhenDatabaseAuthAndNoAdminAndNotFullyInitialized() {
        bootstrapState();

        assertThat(service.isInitialAdminUserRequired()).isTrue();
    }

    @Test
    public void notRequiredWhenAuthProviderIsPam() {
        when(userAuthenticationConfig.getAuthenticationProvider()).thenReturn(AuthenticationProviderType.PAM);

        assertThat(service.isInitialAdminUserRequired()).isFalse();
    }

    @Test
    public void notRequiredWhenAdminUserAlreadyExists() {
        when(userAuthenticationConfig.getAuthenticationProvider()).thenReturn(AuthenticationProviderType.DATABASE);
        when(adminUserService.count()).thenReturn(1L);

        assertThat(service.isInitialAdminUserRequired()).isFalse();
    }

    @Test
    public void notRequiredWhenServerFullyInitialized() throws Exception {
        when(userAuthenticationConfig.getAuthenticationProvider()).thenReturn(AuthenticationProviderType.DATABASE);
        when(adminUserService.count()).thenReturn(0L);
        stubServerFullyInitialized();

        assertThat(service.isInitialAdminUserRequired()).isFalse();
    }

    @Test
    public void createDelegatesToAdminUserServiceWithFullPamRoleSet() {
        bootstrapState();

        service.createInitialAdminUser(USERNAME, PASSWORD);

        ArgumentCaptor<AdminUser> captor = ArgumentCaptor.forClass(AdminUser.class);
        verify(adminUserService).create(captor.capture());
        AdminUser created = captor.getValue();
        assertThat(created.getUsername()).isEqualTo(USERNAME);
        assertThat(created.getPassword()).isEqualTo(PASSWORD);
        assertThat(created.getRoles()).isEqualTo(EXPECTED_ROLES);
    }

    @Test
    public void createRejectedWhenAuthProviderIsPam() {
        when(userAuthenticationConfig.getAuthenticationProvider()).thenReturn(AuthenticationProviderType.PAM);

        assertThatThrownBy(() -> service.createInitialAdminUser(USERNAME, PASSWORD))
                .isInstanceOf(InitialAdminUserNotAllowedException.class);
        verify(adminUserService, never()).create(org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void createRejectedWhenAdminUserAlreadyExists() {
        when(userAuthenticationConfig.getAuthenticationProvider()).thenReturn(AuthenticationProviderType.DATABASE);
        when(adminUserService.count()).thenReturn(1L);

        assertThatThrownBy(() -> service.createInitialAdminUser(USERNAME, PASSWORD))
                .isInstanceOf(InitialAdminUserNotAllowedException.class);
        verify(adminUserService, never()).create(org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void createRejectedWhenServerFullyInitialized() throws Exception {
        when(userAuthenticationConfig.getAuthenticationProvider()).thenReturn(AuthenticationProviderType.DATABASE);
        when(adminUserService.count()).thenReturn(0L);
        stubServerFullyInitialized();

        assertThatThrownBy(() -> service.createInitialAdminUser(USERNAME, PASSWORD))
                .isInstanceOf(InitialAdminUserNotAllowedException.class);
        verify(adminUserService, never()).create(org.mockito.ArgumentMatchers.any());
    }

    private void bootstrapState() {
        when(userAuthenticationConfig.getAuthenticationProvider()).thenReturn(AuthenticationProviderType.DATABASE);
        when(adminUserService.count()).thenReturn(0L);
    }

    private void stubServerFullyInitialized() throws Exception {
        when(confClientRpcClient.getConfigurationAnchor()).thenReturn(ANCHOR_BYTES);

        ServerConfEntity serverConf = new ServerConfEntity();
        serverConf.setServerCode("SS1");
        serverConf.setOwner(new ClientEntity());
        when(serverConfRepository.getServerConf()).thenReturn(serverConf);

        TokenInfo softwareToken = new TokenTestUtils.TokenInfoBuilder()
                .id(SOFTWARE_TOKEN_ID)
                .status(TokenStatusInfo.OK)
                .build();
        when(signerRpcClient.getTokens()).thenReturn(List.of(softwareToken));
    }
}
