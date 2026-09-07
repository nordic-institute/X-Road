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

package org.niis.xroad.configuration.migration;

import org.junit.jupiter.api.Test;
import org.springframework.vault.core.VaultKeyValueOperations;
import org.springframework.vault.support.VaultResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MigrationVaultClientTest {

    @Test
    void testSetTokenPinCallsCorrectVaultPath() {
        VaultKeyValueOperations kvOps = mock(VaultKeyValueOperations.class);
        MigrationVaultClient client = new MigrationVaultClient(kvOps);

        client.setTokenPin("tok1", "s3cr3t".toCharArray());

        Map<String, String> expected = new HashMap<>();
        expected.put("pin", "s3cr3t");
        verify(kvOps).put(eq("signer/token-pins/tok1"), eq(expected));
    }

    @Test
    void testGetTokenPinReturnsDecodedPin() {
        VaultKeyValueOperations kvOps = mock(VaultKeyValueOperations.class);
        VaultResponse response = new VaultResponse();
        Map<String, Object> data = new HashMap<>();
        data.put("pin", "s3cr3t");
        response.setData(data);
        when(kvOps.get("signer/token-pins/tok1")).thenReturn(response);

        MigrationVaultClient client = new MigrationVaultClient(kvOps);
        Optional<char[]> result = client.getTokenPin("tok1");

        assertThat(result).isPresent();
        assertThat(new String(result.get())).isEqualTo("s3cr3t");
    }

    @Test
    void testGetTokenPinReturnsEmptyWhenResponseNull() {
        VaultKeyValueOperations kvOps = mock(VaultKeyValueOperations.class);
        when(kvOps.get("signer/token-pins/tok1")).thenReturn(null);

        MigrationVaultClient client = new MigrationVaultClient(kvOps);

        assertThat(client.getTokenPin("tok1")).isEmpty();
    }

    @Test
    void testGetTokenPinReturnsEmptyWhenDataNull() {
        VaultKeyValueOperations kvOps = mock(VaultKeyValueOperations.class);
        VaultResponse response = new VaultResponse();   // data is null by default
        when(kvOps.get("signer/token-pins/tok1")).thenReturn(response);

        MigrationVaultClient client = new MigrationVaultClient(kvOps);

        assertThat(client.getTokenPin("tok1")).isEmpty();
    }

    @Test
    void testGetTokenPinReturnsEmptyWhenPinKeyMissing() {
        VaultKeyValueOperations kvOps = mock(VaultKeyValueOperations.class);
        VaultResponse response = new VaultResponse();
        response.setData(new HashMap<>());   // no "pin" entry
        when(kvOps.get("signer/token-pins/tok1")).thenReturn(response);

        MigrationVaultClient client = new MigrationVaultClient(kvOps);

        assertThat(client.getTokenPin("tok1")).isEmpty();
    }

    @Test
    void testDeleteTokenPinCallsCorrectVaultPath() {
        VaultKeyValueOperations kvOps = mock(VaultKeyValueOperations.class);
        MigrationVaultClient client = new MigrationVaultClient(kvOps);

        client.deleteTokenPin("tok1");

        verify(kvOps).delete("signer/token-pins/tok1");
    }

}
