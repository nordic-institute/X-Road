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
package org.niis.xroad.edc.extension.jetty;

import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.conf.InternalSSLKey;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.vault.VaultClient;

import java.security.cert.X509Certificate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DsHttpsKeyStoreLoaderTest {

    @Mock
    private VaultClient vaultClient;

    @Test
    void loadsAKeyStoreServingTheStoredCertificate() throws Exception {
        var credentials = TestCertUtil.getInternalKey();
        when(vaultClient.getDsHttpsTlsCredentials()).thenReturn(new InternalSSLKey(credentials.key, credentials.certChain));

        var loaded = new DsHttpsKeyStoreLoader(vaultClient).load();

        var servedCert = (X509Certificate) loaded.material().getCertificate(InternalSSLKey.KEY_ALIAS);
        assertThat(servedCert).isEqualTo(credentials.certChain[0]);
    }

    @Test
    void theFingerprintChangesWhenTheCertificateChainChanges() throws Exception {
        var first = TestCertUtil.getInternalKey();
        var second = TestCertUtil.getOcspSigner();
        var loader = new DsHttpsKeyStoreLoader(vaultClient);

        when(vaultClient.getDsHttpsTlsCredentials()).thenReturn(new InternalSSLKey(first.key, first.certChain));
        var loadedFirst = loader.load();

        when(vaultClient.getDsHttpsTlsCredentials()).thenReturn(new InternalSSLKey(second.key, second.certChain));
        var loadedSecond = loader.load();

        assertThat(loadedFirst.fingerprint()).isNotEqualTo(loadedSecond.fingerprint());
    }

    @Test
    void theFingerprintIsStableForTheSameCertificateChain() throws Exception {
        var credentials = TestCertUtil.getInternalKey();
        when(vaultClient.getDsHttpsTlsCredentials()).thenReturn(new InternalSSLKey(credentials.key, credentials.certChain));
        var loader = new DsHttpsKeyStoreLoader(vaultClient);

        assertThat(loader.load().fingerprint()).isEqualTo(loader.load().fingerprint());
    }

    @Test
    void anEmptyCertificateChainIsReportedAsCertificatePending() throws Exception {
        var credentials = TestCertUtil.getInternalKey();
        when(vaultClient.getDsHttpsTlsCredentials()).thenReturn(new InternalSSLKey(credentials.key, new X509Certificate[0]));

        assertThatThrownBy(() -> new DsHttpsKeyStoreLoader(vaultClient).load())
                .isInstanceOf(DsTlsKeyStoreLoadException.class)
                .hasMessageContaining("certificate pending");
    }

    @Test
    void aMissingSecretIsReportedAsNotProvisioned() throws Exception {
        when(vaultClient.getDsHttpsTlsCredentials()).thenThrow(missingSecret());

        assertThatThrownBy(() -> new DsHttpsKeyStoreLoader(vaultClient).load())
                .isInstanceOf(DsTlsKeyStoreLoadException.class)
                .hasMessageContaining("No DataSpace TLS certificate found");
    }

    @Test
    void aVaultOutageIsNotReportedAsNotProvisioned() throws Exception {
        when(vaultClient.getDsHttpsTlsCredentials()).thenThrow(new IllegalStateException("connection refused"));

        assertThatThrownBy(() -> new DsHttpsKeyStoreLoader(vaultClient).load())
                .isInstanceOf(DsTlsKeyStoreLoadException.class)
                .hasMessageContaining("Could not reach OpenBao")
                .hasMessageNotContaining("No DataSpace TLS certificate found");
    }

    private static XrdRuntimeException missingSecret() {
        return XrdRuntimeException.systemException(ErrorCode.MISSING_SECRET)
                .details("Secret not found at path: tls/ds-https")
                .build();
    }
}
