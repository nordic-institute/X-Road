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
import org.junit.jupiter.api.io.TempDir;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.core.node.NodeIdentity;
import org.niis.xroad.migration.tokenpin.AutoLoginScriptExecutor;
import org.niis.xroad.migration.tokenpin.ScriptExecutionResult;
import org.niis.xroad.migration.tokenpin.TokenPinMigrationResult;
import org.niis.xroad.migration.tokenpin.TokenPinMigrator;
import org.springframework.vault.core.VaultKeyValueOperations;
import org.springframework.vault.support.VaultResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MigrationVaultClientTest {

    @Test
    void testSetTokenPinCallsCorrectVaultPath() {
        VaultKeyValueOperations kvOps = mock(VaultKeyValueOperations.class);
        MigrationVaultClient client = new MigrationVaultClient(kvOps, NodeIdentity.resolve("node_0"));

        client.setTokenPin("tok1", "s3cr3t".toCharArray());

        Map<String, String> expected = new HashMap<>();
        expected.put("pin", "s3cr3t");
        verify(kvOps).put("signer/token-pins/node_0/tok1", expected);
    }

    @Test
    void testGetTokenPinReturnsDecodedPin() {
        VaultKeyValueOperations kvOps = mock(VaultKeyValueOperations.class);
        VaultResponse response = new VaultResponse();
        Map<String, Object> data = new HashMap<>();
        data.put("pin", "s3cr3t");
        response.setData(data);
        when(kvOps.get("signer/token-pins/node_0/tok1")).thenReturn(response);

        MigrationVaultClient client = new MigrationVaultClient(kvOps, NodeIdentity.resolve("node_0"));
        Optional<char[]> result = client.getTokenPin("tok1");

        assertThat(result).isPresent();
        assertThat(new String(result.get())).isEqualTo("s3cr3t");
    }

    @Test
    void testGetTokenPinReturnsEmptyWhenResponseNull() {
        VaultKeyValueOperations kvOps = mock(VaultKeyValueOperations.class);
        when(kvOps.get("signer/token-pins/node_0/tok1")).thenReturn(null);

        MigrationVaultClient client = new MigrationVaultClient(kvOps, NodeIdentity.resolve("node_0"));

        assertThat(client.getTokenPin("tok1")).isEmpty();
    }

    @Test
    void testGetTokenPinReturnsEmptyWhenDataNull() {
        VaultKeyValueOperations kvOps = mock(VaultKeyValueOperations.class);
        VaultResponse response = new VaultResponse();   // data is null by default
        when(kvOps.get("signer/token-pins/node_0/tok1")).thenReturn(response);

        MigrationVaultClient client = new MigrationVaultClient(kvOps, NodeIdentity.resolve("node_0"));

        assertThat(client.getTokenPin("tok1")).isEmpty();
    }

    @Test
    void testGetTokenPinReturnsEmptyWhenPinKeyMissing() {
        VaultKeyValueOperations kvOps = mock(VaultKeyValueOperations.class);
        VaultResponse response = new VaultResponse();
        response.setData(new HashMap<>());   // no "pin" entry
        when(kvOps.get("signer/token-pins/node_0/tok1")).thenReturn(response);

        MigrationVaultClient client = new MigrationVaultClient(kvOps, NodeIdentity.resolve("node_0"));

        assertThat(client.getTokenPin("tok1")).isEmpty();
    }

    @Test
    void testDeleteTokenPinCallsCorrectVaultPath() {
        VaultKeyValueOperations kvOps = mock(VaultKeyValueOperations.class);
        MigrationVaultClient client = new MigrationVaultClient(kvOps, NodeIdentity.resolve("node_0"));

        client.deleteTokenPin("tok1");

        verify(kvOps).delete("signer/token-pins/node_0/tok1");
    }

    @Test
    void testPinsAreWrittenReadAndDeletedUnderTheGivenNode() {
        VaultKeyValueOperations kvOps = mock(VaultKeyValueOperations.class);
        VaultResponse response = new VaultResponse();
        response.setData(new HashMap<>(Map.of("pin", "s3cr3t")));
        when(kvOps.get("signer/token-pins/node_1/tok1")).thenReturn(response);
        MigrationVaultClient client = new MigrationVaultClient(kvOps, NodeIdentity.resolve("node_1"));

        client.setTokenPin("tok1", "s3cr3t".toCharArray());
        Optional<char[]> pin = client.getTokenPin("tok1");
        client.deleteTokenPin("tok1");

        verify(kvOps).put("signer/token-pins/node_1/tok1", Map.of("pin", "s3cr3t"));
        assertThat(pin).isPresent();
        verify(kvOps).delete("signer/token-pins/node_1/tok1");
        verify(kvOps, never()).put(startsWith("signer/token-pins/node_0/"), any());
    }

    @Test
    void testMigrationStoresPinsUnderTheNodeAndASecondRunChangesNothing(@TempDir Path tempDir) throws IOException {
        Map<String, Map<String, Object>> store = new HashMap<>();
        VaultKeyValueOperations kvOps = mock(VaultKeyValueOperations.class);
        doAnswer(invocation -> {
            store.put(invocation.getArgument(0), new HashMap<>(invocation.<Map<String, Object>>getArgument(1)));
            return null;
        }).when(kvOps).put(anyString(), any());
        when(kvOps.get(anyString())).thenAnswer(invocation -> {
            Map<String, Object> data = store.get(invocation.<String>getArgument(0));
            if (data == null) {
                return null;
            }
            VaultResponse response = new VaultResponse();
            response.setData(data);
            return response;
        });
        AutoLoginScriptExecutor executor = mock(AutoLoginScriptExecutor.class);
        when(executor.execute(any())).thenReturn(new ScriptExecutionResult(true, "0:1111\nsofttoken-1:2222\n", "", 0));
        Path script = tempDir.resolve("fetch-pin.sh");
        Files.writeString(script, "stub");
        assertThat(script.toFile().setExecutable(true)).isTrue();
        TokenPinMigrator migrator = new TokenPinMigrator(new MigrationVaultClient(kvOps, NodeIdentity.resolve("node_1")), executor);

        TokenPinMigrationResult first = migrator.migrateFromScript(script);
        Map<String, Map<String, Object>> afterFirstRun = new HashMap<>(store);
        TokenPinMigrationResult second = migrator.migrateFromScript(script);

        assertThat(first.successfulTokens()).containsExactlyInAnyOrder("0", "softtoken-1");
        assertThat(store.keySet()).containsExactlyInAnyOrder(
                "signer/token-pins/node_1/0", "signer/token-pins/node_1/softtoken-1");
        assertThat(second.skippedTokens()).containsExactlyInAnyOrder("0", "softtoken-1");
        assertThat(second.successfulTokens()).isEmpty();
        assertThat(store).isEqualTo(afterFirstRun);
        verify(kvOps, times(2)).put(anyString(), any());
    }

    @Test
    void testBlankNodeNameResolvesToDefaultNode() {
        VaultKeyValueOperations kvOps = mock(VaultKeyValueOperations.class);

        new MigrationVaultClient(kvOps, NodeIdentity.resolve("")).deleteTokenPin("tok1");

        verify(kvOps).delete("signer/token-pins/node_0/tok1");
    }

    @Test
    void testInvalidNodeNameIsRejectedWithTheRuleAndNothingIsWritten() {
        VaultKeyValueOperations kvOps = mock(VaultKeyValueOperations.class);

        assertThatThrownBy(() -> NodeIdentity.resolve("_bad"))
                .isInstanceOf(XrdRuntimeException.class)
                .hasMessageContaining("^[A-Za-z0-9][A-Za-z0-9_-]{0,62}$");

        verifyNoInteractions(kvOps);
    }

}
