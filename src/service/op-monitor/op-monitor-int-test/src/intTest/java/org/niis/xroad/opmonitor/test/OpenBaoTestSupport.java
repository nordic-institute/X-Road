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
package org.niis.xroad.opmonitor.test;

import io.quarkus.vault.VaultKVSecretEngine;
import io.quarkus.vault.client.http.jdk.JDKVaultHttpClient;
import io.quarkus.vault.runtime.VaultConfigHolder;
import io.quarkus.vault.runtime.VaultKvManager;
import io.quarkus.vault.runtime.config.VaultRuntimeConfig;
import org.niis.xroad.common.vault.VaultClient;
import org.niis.xroad.common.vault.quarkus.QuarkusVaultClient;
import org.niis.xroad.opmonitor.test.container.OpMonitorContainerSetup;

import java.net.http.HttpClient;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Access to the OpenBao of the op-monitor stack for the test classes that touch the op-monitor CA record.
 */
final class OpenBaoTestSupport {

    /** Resource lock of the tests that read or write the op-monitor CA record. */
    static final String OP_MONITOR_CA_RECORD = "op-monitor-ca-record";

    static final String KV_MOUNT = "xrd-secret";

    private static final String OP_MONITOR_CA_RECORD_PATH = KV_MOUNT + "/tls/opmonitor-ca";

    private OpenBaoTestSupport() {
    }

    static io.quarkus.vault.client.VaultClient vaultApi(OpMonitorContainerSetup stack) {
        return io.quarkus.vault.client.VaultClient.builder()
                .baseUrl(stack.openBaoUrl())
                .executor(new JDKVaultHttpClient(HttpClient.newHttpClient()))
                .clientToken(stack.openBaoClientToken())
                .build();
    }

    static VaultClient quarkusVaultClient(io.quarkus.vault.client.VaultClient vaultApi) {
        return new QuarkusVaultClient(new VaultKVSecretEngine(new VaultKvManager(vaultApi, quarkusConfigHolder())));
    }

    static void deleteOpMonitorCaRecord(OpMonitorContainerSetup stack) {
        var result = stack.execInContainer(OpMonitorContainerSetup.OPENBAO, "bao", "delete", OP_MONITOR_CA_RECORD_PATH);
        assertThat(result.getExitCode()).as("bao delete " + OP_MONITOR_CA_RECORD_PATH).isZero();
    }

    private static VaultConfigHolder quarkusConfigHolder() {
        var config = mock(VaultRuntimeConfig.class);
        when(config.kvSecretEngineVersion()).thenReturn(1);
        when(config.kvSecretEngineMountPath()).thenReturn(KV_MOUNT);
        when(config.kvSecretEngineAlias()).thenReturn(Map.of());
        return new VaultConfigHolder().setVaultRuntimeConfig(config);
    }
}
