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
package org.niis.xroad.edc.extension.vault;

import io.quarkus.vault.client.VaultClient;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.properties.config.keys.CommonConfigKeys;
import org.niis.xroad.common.vault.ResilientVaultCall;
import org.niis.xroad.common.vault.quarkus.QuarkusVaultFailures;

import static org.niis.xroad.edc.extension.vault.XRoadEdcVaultExtension.NAME;

/**
 * Provides the EDC {@link Vault} backed by OpenBao through the Quarkus vault client, retrying server errors
 * within {@code xroad.common.vault-retry.budget}. The Quarkus {@link VaultClient} and {@link XRoadConfig} are
 * Quarkus beans, so OpenBao TLS trust stays with the Quarkus vault settings.
 */
@Extension(NAME)
@Provides(Vault.class)
public class XRoadEdcVaultExtension implements ServiceExtension {

    static final String NAME = "X-Road EDC Vault";

    @Inject
    private VaultClient vaultClient;

    @Inject
    private XRoadConfig config;

    @Override
    public String name() {
        return NAME;
    }

    @Provider
    public Vault vault() {
        var resilientCall = new ResilientVaultCall(QuarkusVaultFailures::isRetryable,
                config.value(CommonConfigKeys.VAULT_RETRY_BUDGET));
        return new XRoadEdcVault(vaultClient, resilientCall);
    }
}
