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
package org.niis.xroad.common.vault.quarkus;

import ee.ria.xroad.common.conf.InternalSSLKey;

import io.quarkus.vault.VaultKVSecretEngine;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.vault.VaultClient;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.vault.VaultClient.DS_HTTPS_TLS_CREDENTIALS_PATH;

@ExtendWith(MockitoExtension.class)
class QuarkusVaultClientTest {

    @Mock
    private VaultKVSecretEngine kvSecretEngine;

    @Test
    void createDsHttpsTlsCredentialsWritesKeyAndCertificateToTheDsHttpsPath() throws Exception {
        var client = new QuarkusVaultClient(kvSecretEngine);
        var keyPair = generateKeyPair();
        var certificate = selfSignedCertificate(keyPair);
        var internalSslKey = new InternalSSLKey(keyPair.getPrivate(), new X509Certificate[] {certificate});

        client.createDsHttpsTlsCredentials(internalSslKey);

        @SuppressWarnings("unchecked")
        var captor = ArgumentCaptor.forClass(Map.class);
        verify(kvSecretEngine).writeSecret(eq(DS_HTTPS_TLS_CREDENTIALS_PATH), captor.capture());
        Map<String, String> stored = captor.getValue();
        assertThat(stored.get(VaultClient.CERTIFICATE_KEY)).contains("BEGIN CERTIFICATE");
        assertThat(stored.get(VaultClient.PRIVATEKEY_KEY)).contains("BEGIN PRIVATE KEY");
    }

    @Test
    void getDsHttpsTlsCredentialsReadsFromTheDsHttpsPathAndReconstructsTheKeyAndCertificate() throws Exception {
        var client = new QuarkusVaultClient(kvSecretEngine);
        var keyPair = generateKeyPair();
        var certificate = selfSignedCertificate(keyPair);
        var secret = Map.of(
                VaultClient.CERTIFICATE_KEY, client.toPem(certificate),
                VaultClient.PRIVATEKEY_KEY, client.toPem(keyPair.getPrivate()));
        when(kvSecretEngine.readSecret(DS_HTTPS_TLS_CREDENTIALS_PATH)).thenReturn(secret);

        var result = client.getDsHttpsTlsCredentials();

        assertThat(result.getKey()).isEqualTo(keyPair.getPrivate());
        assertThat(result.getCertChain()).containsExactly(certificate);
    }

    @Test
    void getDsHttpsTlsCredentialsThrowsAnActionableErrorWhenTheSlotIsEmpty() {
        var client = new QuarkusVaultClient(kvSecretEngine);
        when(kvSecretEngine.readSecret(DS_HTTPS_TLS_CREDENTIALS_PATH)).thenReturn(null);

        assertThatThrownBy(client::getDsHttpsTlsCredentials).isInstanceOf(XrdRuntimeException.class);
    }

    @Test
    void roundTripsKeyAndCertificateThroughCreateThenGet() throws Exception {
        var client = new QuarkusVaultClient(kvSecretEngine);
        var keyPair = generateKeyPair();
        var certificate = selfSignedCertificate(keyPair);
        var internalSslKey = new InternalSSLKey(keyPair.getPrivate(), new X509Certificate[] {certificate});

        var storage = new HashMap<String, String>();
        doAnswer(invocation -> {
            storage.putAll(invocation.getArgument(1));
            return null;
        }).when(kvSecretEngine).writeSecret(eq(DS_HTTPS_TLS_CREDENTIALS_PATH), anyMap());
        when(kvSecretEngine.readSecret(DS_HTTPS_TLS_CREDENTIALS_PATH)).thenAnswer(invocation -> storage);

        client.createDsHttpsTlsCredentials(internalSslKey);
        var reloaded = client.getDsHttpsTlsCredentials();

        assertThat(reloaded.getKey()).isEqualTo(keyPair.getPrivate());
        assertThat(reloaded.getCertChain()).containsExactly(certificate);
    }

    private static KeyPair generateKeyPair() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static X509Certificate selfSignedCertificate(KeyPair keyPair) throws Exception {
        var subject = new X500Name("CN=ds-https-test");
        var now = Instant.now();
        var certificateBuilder = new JcaX509v3CertificateBuilder(
                subject,
                BigInteger.valueOf(System.nanoTime()),
                Date.from(now),
                Date.from(now.plus(30, ChronoUnit.DAYS)),
                subject,
                keyPair.getPublic());
        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        var holder = certificateBuilder.build(signer);
        return new JcaX509CertificateConverter().getCertificate(holder);
    }
}
