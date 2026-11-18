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
import io.quarkus.vault.runtime.VaultConfigHolder;
import io.quarkus.vault.runtime.client.VaultClientProducer;
import io.quarkus.vault.runtime.config.VaultRuntimeConfig;
import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.vertx.core.Vertx;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Map;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TestVaultClients {

    private static final int CLIENT_TIMEOUT_SECONDS = 1;

    static VaultClient connectedTo(Vertx vertx, int port) {
        var properties = Map.of(
                "quarkus.vault.url", "http://127.0.0.1:" + port,
                "quarkus.vault.authentication.client-token", "test-token",
                "quarkus.vault.connect-timeout", CLIENT_TIMEOUT_SECONDS + "S",
                "quarkus.vault.read-timeout", CLIENT_TIMEOUT_SECONDS + "S");
        var config = new SmallRyeConfigBuilder()
                .withMapping(VaultRuntimeConfig.class)
                .withSources(new PropertiesConfigSource(properties, "test", 500))
                .build()
                .getConfigMapping(VaultRuntimeConfig.class);
        var holder = new VaultConfigHolder().setVaultRuntimeConfig(config);
        return new VaultClientProducer().sharedVaultClient(vertx, holder, false);
    }
}
