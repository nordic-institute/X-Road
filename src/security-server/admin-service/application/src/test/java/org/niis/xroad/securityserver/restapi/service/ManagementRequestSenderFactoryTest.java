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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.rpc.VaultKeyProvider;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.securityserver.restapi.cache.CurrentSecurityServerId;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.niis.xroad.securityserver.restapi.util.TestUtils;

import javax.net.ssl.KeyManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManagementRequestSenderFactoryTest {

    @Mock
    private GlobalConfProvider globalConfProvider;

    private ManagementRequestSenderFactory factory;

    @BeforeEach
    void setUp() {
        var adminServiceProperties = mock(AdminServiceProperties.class);
        var vaultKeyProvider = mock(VaultKeyProvider.class);
        var keyManager = mock(KeyManager.class);
        when(vaultKeyProvider.getKeyManager()).thenReturn(keyManager);
        when(adminServiceProperties.getAuthCertRegSignatureDigestAlgorithmId()).thenReturn("SHA-256");
        when(globalConfProvider.getManagementRequestService()).thenReturn(TestUtils.OWNER_SERVER_ID.getOwner());
        factory = new ManagementRequestSenderFactory(null, null, globalConfProvider,
                new CurrentSecurityServerId(TestUtils.OWNER_SERVER_ID), vaultKeyProvider, adminServiceProperties);
    }

    @Test
    void createBuildsTheSenderForTheOwnerWithoutCheckingTheGlobalConfValidity() {
        assertThat(factory.create()).isNotNull();

        verify(globalConfProvider, never()).verifyValidity();
        verify(globalConfProvider).getManagementRequestService();
    }

    @Test
    void createForBuildsTheSenderForTheGivenSenderWithoutCheckingTheGlobalConfValidity() {
        assertThat(factory.createFor(TestUtils.OWNER_SERVER_ID.getOwner())).isNotNull();

        verify(globalConfProvider, never()).verifyValidity();
        verify(globalConfProvider).getManagementRequestService();
    }
}
