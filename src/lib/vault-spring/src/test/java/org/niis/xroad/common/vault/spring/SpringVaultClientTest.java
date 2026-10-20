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
package org.niis.xroad.common.vault.spring;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.vault.AcmeAccountKey;
import org.niis.xroad.common.vault.DsTlsEnrollmentMethod;
import org.niis.xroad.common.vault.DsTlsEnrollmentStatus;
import org.niis.xroad.common.vault.VaultClient;
import org.springframework.vault.VaultException;
import org.springframework.vault.core.VaultKeyValueOperations;
import org.springframework.vault.support.VaultResponse;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpringVaultClientTest {

    @Mock
    private VaultKeyValueOperations vaultKeyValueOperations;

    private final Map<String, Map<String, Object>> secretsByPath = new HashMap<>();

    private SpringVaultClient vaultClient;

    @BeforeEach
    void setUp() {
        vaultClient = new SpringVaultClient(vaultKeyValueOperations);

        lenient().doAnswer(invocation -> {
            String path = invocation.getArgument(0);
            @SuppressWarnings("unchecked")
            Map<String, Object> secret = (Map<String, Object>) invocation.getArgument(1);
            secretsByPath.put(path, new HashMap<>(secret));
            return null;
        }).when(vaultKeyValueOperations).put(any(), any());

        lenient().when(vaultKeyValueOperations.get(any())).thenAnswer(invocation -> {
            String path = invocation.getArgument(0);
            Map<String, Object> data = secretsByPath.get(path);
            if (data == null) {
                return null;
            }
            var response = new VaultResponse();
            response.setData(data);
            return response;
        });

        lenient().doAnswer(invocation -> {
            secretsByPath.remove(invocation.<String>getArgument(0));
            return null;
        }).when(vaultKeyValueOperations).delete(any());

        lenient().when(vaultKeyValueOperations.list(any())).thenAnswer(invocation -> {
            String basePath = invocation.getArgument(0);
            String prefix = basePath + "/";
            return secretsByPath.keySet().stream()
                    .filter(path -> path.startsWith(prefix))
                    .map(path -> path.substring(prefix.length()))
                    .toList();
        });
    }

    @Test
    void shouldStoreAndRetrieveAcmeAccountKeyForAlias() throws Exception {
        String alias = "auth_CS:ORG:MEMBER1";
        KeyPair keyPair = generateRsaKeyPair();
        Instant expiresAt = Instant.now().plus(90, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        var stored = new AcmeAccountKey(keyPair.getPrivate(), keyPair.getPublic(), expiresAt);

        vaultClient.createAcmeAccountKey(alias, stored);
        var retrieved = vaultClient.getAcmeAccountKey(alias).orElseThrow();

        assertThat(retrieved.privateKey()).isEqualTo(keyPair.getPrivate());
        assertThat(retrieved.publicKey()).isEqualTo(keyPair.getPublic());
        assertThat(retrieved.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void shouldReturnEmptyWhenNoAccountKeyPairExistsYetForAlias() {
        var retrieved = vaultClient.getAcmeAccountKey("auth_CS:ORG:UNKNOWN-MEMBER");

        assertThat(retrieved).isEmpty();
    }

    @Test
    void shouldKeepTwoAliasesIndependent() throws Exception {
        String aliasOne = "auth_CS:ORG:MEMBER1";
        String aliasTwo = "sign_CS:ORG:MEMBER1";

        KeyPair keyPairOne = generateRsaKeyPair();
        Instant expiresAtOne = Instant.now().plus(90, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        var storedOne = new AcmeAccountKey(keyPairOne.getPrivate(), keyPairOne.getPublic(), expiresAtOne);

        KeyPair keyPairTwo = generateRsaKeyPair();
        Instant expiresAtTwo = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        var storedTwo = new AcmeAccountKey(keyPairTwo.getPrivate(), keyPairTwo.getPublic(), expiresAtTwo);

        vaultClient.createAcmeAccountKey(aliasOne, storedOne);
        vaultClient.createAcmeAccountKey(aliasTwo, storedTwo);

        var retrievedOne = vaultClient.getAcmeAccountKey(aliasOne).orElseThrow();
        var retrievedTwo = vaultClient.getAcmeAccountKey(aliasTwo).orElseThrow();

        assertThat(retrievedOne.privateKey()).isEqualTo(keyPairOne.getPrivate());
        assertThat(retrievedOne.expiresAt()).isEqualTo(expiresAtOne);

        assertThat(retrievedTwo.privateKey()).isEqualTo(keyPairTwo.getPrivate());
        assertThat(retrievedTwo.expiresAt()).isEqualTo(expiresAtTwo);

        assertThat(retrievedOne.privateKey()).isNotEqualTo(retrievedTwo.privateKey());
    }

    @Test
    void shouldStoreAndRetrieveDsTlsEnrollmentStatus() {
        Instant nextRenewalTime = Instant.now().plus(60, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        var stored = new DsTlsEnrollmentStatus(DsTlsEnrollmentMethod.ACME, nextRenewalTime, "some error");

        vaultClient.createDsTlsEnrollmentStatus(stored);
        var retrieved = vaultClient.getDsTlsEnrollmentStatus().orElseThrow();

        assertThat(retrieved).isEqualTo(stored);
    }

    @Test
    void shouldStoreAndRetrieveDsTlsEnrollmentStatusWithNoRenewalTimeOrError() {
        var stored = new DsTlsEnrollmentStatus(DsTlsEnrollmentMethod.MANUAL, null, null);

        vaultClient.createDsTlsEnrollmentStatus(stored);
        var retrieved = vaultClient.getDsTlsEnrollmentStatus().orElseThrow();

        assertThat(retrieved).isEqualTo(stored);
    }

    @Test
    void shouldReturnEmptyWhenNoDsTlsEnrollmentStatusHasEverBeenRecorded() {
        var retrieved = vaultClient.getDsTlsEnrollmentStatus();

        assertThat(retrieved).isEmpty();
    }

    @Test
    void shouldStoreAndRetrieveADsTlsEnrollmentStatusWithNoMethodRecorded() {
        var stored = new DsTlsEnrollmentStatus(null, null, null);

        vaultClient.createDsTlsEnrollmentStatus(stored);
        var retrieved = vaultClient.getDsTlsEnrollmentStatus().orElseThrow();

        assertThat(retrieved).isEqualTo(stored);
        assertThat(retrieved.method()).isNull();
    }

    @Test
    void shouldStoreAndRetrieveAgreementTokenSigningKeys() {
        String keyPairOne = "{\"kty\":\"EC\",\"kid\":\"1\"}";
        String keyPairTwo = "{\"kty\":\"EC\",\"kid\":\"2\"}";

        vaultClient.createAgreementTokenSigningKey("1", keyPairOne);
        vaultClient.createAgreementTokenSigningKey("2", keyPairTwo);

        var retrieved = vaultClient.getAgreementTokenSigningKeys();

        assertThat(retrieved).containsExactlyInAnyOrderEntriesOf(Map.of("1", keyPairOne, "2", keyPairTwo));
    }

    @Test
    void shouldReturnEmptyMapWhenNoAgreementTokenSigningKeyHasEverBeenStored() {
        var retrieved = vaultClient.getAgreementTokenSigningKeys();

        assertThat(retrieved).isEmpty();
    }

    @Test
    void shouldStoreAndRetrieveOpMonitorCaWithExactlyTheCertificateField() throws Exception {
        var ca = selfSignedCa("CN=Op-monitor CA");

        vaultClient.createOpMonitorCa(ca);

        assertThat(vaultClient.getOpMonitorCa()).contains(ca);
        assertThat(secretsByPath.get(VaultClient.OPMONITOR_CA_PATH)).containsOnlyKeys(VaultClient.CERTIFICATE_KEY);
        assertThat(VaultClient.OPMONITOR_CA_PATH).isEqualTo("tls/opmonitor-ca");
    }

    @Test
    void shouldReplaceTheStoredOpMonitorCa() throws Exception {
        var first = selfSignedCa("CN=First CA");
        var second = selfSignedCa("CN=Second CA");

        vaultClient.createOpMonitorCa(first);
        vaultClient.createOpMonitorCa(second);

        assertThat(vaultClient.getOpMonitorCa()).contains(second);
    }

    @Test
    void shouldReturnEmptyWhenNoOpMonitorCaIsStored() {
        assertThat(vaultClient.getOpMonitorCa()).isEmpty();
    }

    @Test
    void shouldReadABlankOpMonitorCaRecordAsAbsent() {
        secretsByPath.put(VaultClient.OPMONITOR_CA_PATH, Map.of(VaultClient.CERTIFICATE_KEY, ""));
        assertThat(vaultClient.getOpMonitorCa()).isEmpty();

        secretsByPath.put(VaultClient.OPMONITOR_CA_PATH, Map.of(VaultClient.CERTIFICATE_KEY, " \n"));
        assertThat(vaultClient.getOpMonitorCa()).isEmpty();
    }

    @Test
    void shouldSkipAnAgreementTokenSigningKeyDeletedBetweenListingAndReading() {
        vaultClient.createAgreementTokenSigningKey("1", "jwk-one");
        when(vaultKeyValueOperations.list(any())).thenReturn(List.of("1", "2"));

        var retrieved = vaultClient.getAgreementTokenSigningKeys();

        assertThat(retrieved).containsExactlyEntriesOf(Map.of("1", "jwk-one"));
    }

    @Test
    void shouldPropagateAgreementTokenSigningKeyListingFailureRatherThanReturningEmpty() {
        when(vaultKeyValueOperations.list(any())).thenThrow(new VaultException("vault sealed"));

        assertThatThrownBy(() -> vaultClient.getAgreementTokenSigningKeys())
                .isInstanceOf(VaultException.class);
    }

    @Test
    void shouldWriteAgreementTokenSigningKeyToTheKvV1PathWithNoDataOrCasWrapping() {
        vaultClient.createAgreementTokenSigningKey("1", "some-key-pair-jwk");

        var expectedPath = VaultClient.AGREEMENT_TOKEN_SIGNING_KEYS_BASE_PATH + "/1";
        assertThat(secretsByPath).containsKey(expectedPath);
        assertThat(expectedPath).doesNotContain("/data/").doesNotContain("cas");

        var written = secretsByPath.get(expectedPath);
        assertThat(written).isNotEmpty().containsEntry(VaultClient.PAYLOAD_KEY, "some-key-pair-jwk");
    }

    @Test
    void shouldNotDeleteAnythingWhenStoringOrReadingAgreementTokenSigningKeys() {
        vaultClient.createAgreementTokenSigningKey("1", "some-key-pair-jwk");
        vaultClient.getAgreementTokenSigningKeys();

        verify(vaultKeyValueOperations, never()).delete(anyString());
    }

    @Test
    void shouldDeleteOnlyTheNamedAgreementTokenSigningKey() {
        vaultClient.createAgreementTokenSigningKey("1", "jwk-one");
        vaultClient.createAgreementTokenSigningKey("2", "jwk-two");

        vaultClient.deleteAgreementTokenSigningKey("1");

        verify(vaultKeyValueOperations).delete(VaultClient.AGREEMENT_TOKEN_SIGNING_KEYS_BASE_PATH + "/1");
        verify(vaultKeyValueOperations, never()).delete(VaultClient.AGREEMENT_TOKEN_SIGNING_KEYS_BASE_PATH + "/2");
        assertThat(vaultClient.getAgreementTokenSigningKeys()).containsExactlyEntriesOf(Map.of("2", "jwk-two"));
    }

    @Test
    void shouldPropagateAgreementTokenSigningKeyDeleteFailure() {
        doThrow(new VaultException("permission denied")).when(vaultKeyValueOperations).delete(any());

        assertThatThrownBy(() -> vaultClient.deleteAgreementTokenSigningKey("1"))
                .isInstanceOf(VaultException.class);
    }

    @Test
    void shouldClearTheOpMonitorCaByWritingABlankCertificateField() throws Exception {
        vaultClient.createOpMonitorCa(selfSignedCa("CN=Op-monitor CA"));
        vaultClient.clearOpMonitorCa();
        assertThat(secretsByPath.get(VaultClient.OPMONITOR_CA_PATH)).containsOnlyKeys(VaultClient.CERTIFICATE_KEY);
        assertThat(secretsByPath.get(VaultClient.OPMONITOR_CA_PATH).get(VaultClient.CERTIFICATE_KEY)).isEqualTo("");
        assertThat(vaultClient.getOpMonitorCa()).isEmpty();
    }

    @Test
    void shouldNeverDeleteTheOpMonitorCaSecretWhenClearing() throws Exception {
        vaultClient.createOpMonitorCa(selfSignedCa("CN=Op-monitor CA"));
        vaultClient.clearOpMonitorCa();
        vaultClient.clearOpMonitorCa();
        verify(vaultKeyValueOperations, never()).delete(anyString());
    }

    @Test
    void shouldClearAnOpMonitorCaThatWasNeverStored() {
        vaultClient.clearOpMonitorCa();
        assertThat(vaultClient.getOpMonitorCa()).isEmpty();
        assertThat(secretsByPath.get(VaultClient.OPMONITOR_CA_PATH)).containsOnlyKeys(VaultClient.CERTIFICATE_KEY);
    }

    @Test
    void shouldStoreAnOpMonitorCaAgainAfterItWasCleared() throws Exception {
        var first = selfSignedCa("CN=First CA");
        var second = selfSignedCa("CN=Second CA");
        vaultClient.createOpMonitorCa(first);
        vaultClient.clearOpMonitorCa();
        vaultClient.createOpMonitorCa(second);
        assertThat(vaultClient.getOpMonitorCa()).contains(second);
    }

    private static X509Certificate selfSignedCa(String subjectName) throws Exception {
        var keyPair = generateRsaKeyPair();
        var subject = new X500Name(subjectName);
        var builder = new JcaX509v3CertificateBuilder(subject, BigInteger.ONE, Date.from(Instant.now().minus(1, ChronoUnit.DAYS)),
                Date.from(Instant.now().plus(365, ChronoUnit.DAYS)), subject, keyPair.getPublic())
                .addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }

    private static KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}
