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
package org.niis.xroad.cs.admin.core.dataspace;

import org.junit.jupiter.api.Test;
import org.niis.xroad.common.vault.VaultClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IssuerKeyGuardImplTest {

    private static final int SIGNING_STATE = 200;
    private static final int RETIRED_STATE = 300;

    private final FakeIssuerKeyPairReader keyPairReader = new FakeIssuerKeyPairReader();
    private final VaultClient vaultClient = mock(VaultClient.class);
    private final IssuerKeyGuardImpl guard = new IssuerKeyGuardImpl(keyPairReader, vaultClient);

    @Test
    void answersNoWhenNoSigningKeyExists() {
        assertThat(guard.signingKeySecretPresent()).isFalse();
        verifyNoInteractions(vaultClient);
    }

    @Test
    void answersNoWhenSigningKeySecretIsAbsent() {
        keyPairReader.addKey("issuer-key", SIGNING_STATE, true);
        when(vaultClient.dsIssuerServiceKeypairSecretExists("issuer-key")).thenReturn(false);

        assertThat(guard.signingKeySecretPresent()).isFalse();
    }

    @Test
    void answersYesWhenSigningKeySecretExists() {
        keyPairReader.addKey("issuer-key", SIGNING_STATE, true);
        when(vaultClient.dsIssuerServiceKeypairSecretExists("issuer-key")).thenReturn(true);

        assertThat(guard.signingKeySecretPresent()).isTrue();
    }

    @Test
    void answersYesWhenARetiredKeyWithoutASecretSitsNextToAHealthySigningKey() {
        keyPairReader.addKey("retired-key", RETIRED_STATE, false);
        keyPairReader.addKey("issuer-key", SIGNING_STATE, true);
        when(vaultClient.dsIssuerServiceKeypairSecretExists("retired-key")).thenReturn(false);
        when(vaultClient.dsIssuerServiceKeypairSecretExists("issuer-key")).thenReturn(true);

        assertThat(guard.signingKeySecretPresent()).isTrue();
    }

    @Test
    void answersYesAfterARotationWhereTheOriginalAliasIsRetiredAndAnotherKeySigns() {
        keyPairReader.addKey("issuer-key", RETIRED_STATE, false);
        keyPairReader.addKey("issuer-key-2", SIGNING_STATE, true);
        when(vaultClient.dsIssuerServiceKeypairSecretExists("issuer-key")).thenReturn(false);
        when(vaultClient.dsIssuerServiceKeypairSecretExists("issuer-key-2")).thenReturn(true);

        assertThat(guard.signingKeySecretPresent()).isTrue();
    }

    /**
     * Stands in for the real key-pair table read, applying the same Signing-state selection
     * ({@code state = 200 and is_default_pair}) in memory so a guard test needs no database.
     */
    private static final class FakeIssuerKeyPairReader implements IssuerKeyPairReader {
        private record Row(String alias, int state, boolean isDefaultPair) {
        }

        private final List<Row> rows = new ArrayList<>();

        void addKey(String alias, int state, boolean isDefaultPair) {
            rows.add(new Row(alias, state, isDefaultPair));
        }

        @Override
        public Optional<String> findSigningKeyAlias() {
            return rows.stream()
                    .filter(row -> row.state() == SIGNING_STATE && row.isDefaultPair())
                    .map(Row::alias)
                    .findFirst();
        }
    }
}
