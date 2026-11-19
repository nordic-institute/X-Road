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
package org.niis.xroad.opmonitor.application.healthcheck;

import io.quarkus.vault.client.VaultClient;
import io.smallrye.health.api.AsyncHealthCheck;
import io.smallrye.health.api.HealthRegistry;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.niis.xroad.common.rpc.RpcProperties;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OpMonitorSecretStoreReadinessRegistrarTest {

    private final HealthRegistry readinessRegistry = mock(HealthRegistry.class);
    private final RpcProperties rpcProperties = mock(RpcProperties.class);
    @SuppressWarnings("unchecked")
    private final Instance<VaultClient> vaultClient = mock(Instance.class);

    private final OpMonitorSecretStoreReadinessRegistrar registrar =
            new OpMonitorSecretStoreReadinessRegistrar(readinessRegistry, rpcProperties, vaultClient, true, false);

    @Test
    void openBaoIdentityRegistersTheCheckWithoutCreatingTheClient() {
        when(rpcProperties.identitySource()).thenReturn(RpcProperties.IdentitySource.OPENBAO);

        registrar.onStart(null);

        verify(readinessRegistry).register(eq("Vault connection health check"),
                any(OpMonitorSecretStoreReadinessCheck.class));
        verifyNoInteractions(vaultClient);
    }

    @Test
    void registeredCheckResolvesTheClientWhenCalled() {
        when(rpcProperties.identitySource()).thenReturn(RpcProperties.IdentitySource.OPENBAO);
        var check = ArgumentCaptor.forClass(AsyncHealthCheck.class);

        registrar.onStart(null);
        verify(readinessRegistry).register(eq("Vault connection health check"), check.capture());
        check.getValue().call().await().indefinitely();

        verify(vaultClient).get();
    }

    @Test
    void fileIdentityRegistersNothing() {
        when(rpcProperties.identitySource()).thenReturn(RpcProperties.IdentitySource.FILE);

        registrar.onStart(null);

        verifyNoInteractions(readinessRegistry, vaultClient);
    }
}
