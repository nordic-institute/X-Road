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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.vault.AcmeAccountKey;
import org.niis.xroad.common.vault.DsTlsEnrollmentMethod;
import org.niis.xroad.common.vault.DsTlsEnrollmentStatus;
import org.springframework.vault.core.VaultKeyValueOperations;
import org.springframework.vault.support.VaultResponse;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

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

    private static KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}
