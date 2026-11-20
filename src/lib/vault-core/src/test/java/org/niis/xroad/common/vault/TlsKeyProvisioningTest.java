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
package org.niis.xroad.common.vault;

import ee.ria.xroad.common.conf.InternalSSLKey;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.properties.NodeProperties;

import java.io.IOException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TlsKeyProvisioningTest {

    private final VaultKeyClient keyClient = mock(VaultKeyClient.class);
    private final VaultClient vaultClient = mock(VaultClient.class);

    private final PrivateKey provisionedKey = mock(PrivateKey.class);
    private final X509Certificate identityCert = mock(X509Certificate.class);
    private final X509Certificate trustCert = mock(X509Certificate.class);

    @FunctionalInterface
    private interface Provisioning {
        InternalSSLKey run() throws Exception;
    }

    private InternalSSLKey provision() throws Exception {
        return TlsKeyProvisioning.readOrProvision("admin-service",
                vaultClient::getAdminServiceTlsCredentials,
                vaultClient::createAdminServiceTlsCredentials,
                keyClient);
    }

    private InternalSSLKey provisionOnPrimary() throws Exception {
        return TlsKeyProvisioning.readOrProvisionOnPrimary("admin-service",
                vaultClient::getAdminServiceTlsCredentials,
                vaultClient::createAdminServiceTlsCredentials,
                keyClient);
    }

    private void stubAbsentKey() throws Exception {
        when(vaultClient.getAdminServiceTlsCredentials()).thenThrow(
                XrdRuntimeException.systemException(ErrorCode.MISSING_SECRET).details("absent").build());
    }

    private void stubProvisionedKey() throws Exception {
        when(keyClient.provisionNewCerts()).thenReturn(new VaultKeyClient.VaultKeyData(
                new X509Certificate[] {identityCert}, provisionedKey, new X509Certificate[] {trustCert}));
    }

    private InternalSSLKey runOnNode(boolean secondary, Provisioning provisioning) throws Exception {
        try (MockedStatic<NodeProperties> nodeProperties = mockStatic(NodeProperties.class)) {
            nodeProperties.when(NodeProperties::isSecondaryNode).thenReturn(secondary);

            return provisioning.run();
        }
    }

    private void assertCreatedAndStored(InternalSSLKey result) throws Exception {
        assertThat(result.getKey()).isSameAs(provisionedKey);
        assertThat(result.getCertChain()).containsExactly(identityCert, trustCert);
        var stored = ArgumentCaptor.forClass(InternalSSLKey.class);
        verify(vaultClient).createAdminServiceTlsCredentials(stored.capture());
        assertThat(stored.getValue()).isSameAs(result);
    }

    @Test
    void shouldCreateAndStoreTheKeyWhenTheStoreReportsItAbsent() throws Exception {
        stubAbsentKey();
        stubProvisionedKey();

        assertCreatedAndStored(provision());
    }

    @Test
    void shouldNotChangeAnythingWhenTheKeyExists() throws Exception {
        var existing = new InternalSSLKey(provisionedKey, new X509Certificate[] {identityCert});
        when(vaultClient.getAdminServiceTlsCredentials()).thenReturn(existing);

        assertThat(provision()).isSameAs(existing);
        assertThat(provisionOnPrimary()).isSameAs(existing);

        verifyNoInteractions(keyClient);
        verify(vaultClient, never()).createAdminServiceTlsCredentials(any());
    }

    @Test
    void shouldNotCreateAKeyWhenTheReadFailsForAnotherReason() throws Exception {
        stubProvisionedKey();
        var failures = List.of(
                new IllegalStateException("store unreachable"),
                XrdRuntimeException.systemException(ErrorCode.SECRET_STORE_UNAVAILABLE).details("store down").build(),
                XrdRuntimeException.systemException(ErrorCode.INVALID_PROPERTY_VALUE).details("other").build(),
                new IOException("corrupt"));
        List<Provisioning> variants = List.of(this::provision, this::provisionOnPrimary);

        for (Exception failure : failures) {
            doThrow(failure).when(vaultClient).getAdminServiceTlsCredentials();

            for (Provisioning variant : variants) {
                assertThatThrownBy(variant::run).isSameAs(failure);
            }
        }

        verifyNoInteractions(keyClient);
        verify(vaultClient, never()).createAdminServiceTlsCredentials(any());
    }

    @Test
    void shouldRethrowAndNeverCreateOnSecondaryNodeWhenTheSharedKeyIsAbsent() throws Exception {
        var missing = XrdRuntimeException.systemException(ErrorCode.MISSING_SECRET).details("absent").build();
        when(vaultClient.getAdminServiceTlsCredentials()).thenThrow(missing);

        assertThatThrownBy(() -> runOnNode(true, this::provisionOnPrimary)).isSameAs(missing);

        verifyNoInteractions(keyClient);
        verify(vaultClient, never()).createAdminServiceTlsCredentials(any());
    }

    @Test
    void shouldCreateAndStoreTheSharedKeyOnPrimaryOrStandaloneNodeWhenItIsAbsent() throws Exception {
        stubAbsentKey();
        stubProvisionedKey();

        assertCreatedAndStored(runOnNode(false, this::provisionOnPrimary));
    }

    @Test
    void shouldCreateAndStoreTheKeyWhateverTheNodeTypeSetting() throws Exception {
        stubAbsentKey();
        stubProvisionedKey();

        assertCreatedAndStored(runOnNode(true, this::provision));
    }
}
