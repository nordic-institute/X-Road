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
package org.niis.xroad.securityserver.restapi.config;

import ee.ria.xroad.common.conf.InternalSSLKey;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.properties.NodeProperties;
import org.niis.xroad.common.vault.VaultClient;
import org.niis.xroad.common.vault.VaultKeyClient;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminServiceSslBundleRegistrarTest {

    private final VaultKeyClient keyClient = mock(VaultKeyClient.class);
    private final VaultClient vaultClient = mock(VaultClient.class);

    private final AdminServiceSslBundleRegistrar registrar = new AdminServiceSslBundleRegistrar(keyClient, vaultClient);

    private final XrdRuntimeException missing =
            XrdRuntimeException.systemException(ErrorCode.MISSING_SECRET).details("absent").build();

    private InternalSSLKey resolveOnNode(boolean secondary) throws Exception {
        try (MockedStatic<NodeProperties> nodeProperties = mockStatic(NodeProperties.class)) {
            nodeProperties.when(NodeProperties::isSecondaryNode).thenReturn(secondary);

            return registrar.resolveTlsCredentials();
        }
    }

    @Test
    void shouldCreateTheAdminServiceKeyOnPrimaryOrStandaloneNode() throws Exception {
        var privateKey = mock(PrivateKey.class);
        when(vaultClient.getAdminServiceTlsCredentials()).thenThrow(missing);
        when(keyClient.provisionNewCerts()).thenReturn(new VaultKeyClient.VaultKeyData(
                new X509Certificate[] {mock(X509Certificate.class)}, privateKey, new X509Certificate[0]));

        var result = resolveOnNode(false);

        assertThat(result.getKey()).isSameAs(privateKey);
        verify(vaultClient).createAdminServiceTlsCredentials(any());
    }

    @Test
    void shouldRefuseToCreateTheAdminServiceKeyOnSecondaryNode() throws Exception {
        when(vaultClient.getAdminServiceTlsCredentials()).thenThrow(missing);

        assertThatThrownBy(() -> resolveOnNode(true)).isSameAs(missing);

        verifyNoInteractions(keyClient);
        verify(vaultClient, never()).createAdminServiceTlsCredentials(any());
    }
}
