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
package org.niis.xroad.ds.issuance.application;

import org.eclipse.edc.issuerservice.publisher.LocalStatusListCredentialPublisherExtension;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.rpc.credentials.RpcCredentialsConfigurer;
import org.niis.xroad.common.vault.VaultClient;
import org.niis.xroad.edc.extension.bridge.ExtensionScanGuard;
import org.niis.xroad.globalconf.GlobalConfProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Initializes the Issuer Service's real EDC extension set — every {@link ServiceExtension} the application
 * module's runtime classpath offers, after the application's own {@code excluded-service-extensions} —
 * through the inject → {@code initialize()} → provide steps of the runtime's boot lifecycle, with the
 * identity database settings present and absent, and the statuslist callback setting set and unset. The
 * following {@code prepare()}/{@code start()} phases are not covered: {@link VaultClient}, {@link
 * RpcCredentialsConfigurer} and {@link GlobalConfProvider} are Quarkus CDI beans the container wires in at
 * boot, and only a plain mock of each is registered here, so every other extension on the classpath still
 * initializes for real.
 *
 * <p>The identity database settings below are never connected to: {@code BoundIssuerIdentityHolder} opens
 * its reader connection only from {@code start()}, which this test does not run, so a placeholder JDBC URL
 * is enough to exercise the "identity database settings present" scenarios.
 */
class ExtensionBootTest {

    private static final String CALLBACK_SETTING = "edc.statuslist.callback.address";
    private static final String CALLBACK_ADDRESS = "https://callback.example:6187/statuslist";

    private static final String VAULT_TOKEN_SETTING = "edc.vault.hashicorp.token";
    private static final String VAULT_TOKEN = "test-token";

    private static final String IDENTITY_DB_URL_SETTING = "xroad.db.ds-issuer-service-identity.url";
    private static final String IDENTITY_DB_USERNAME_SETTING = "xroad.db.ds-issuer-service-identity.username";
    private static final String IDENTITY_DB_PASSWORD_SETTING = "xroad.db.ds-issuer-service-identity.password";
    private static final String IDENTITY_DB_SCHEMA_SETTING = "xroad.db.ds-issuer-service-identity.schema";

    @Test
    void bootsWithoutIdentityDbSettingsAndCallbackSet() {
        assertLocalPublisherExtensionNotLoaded(boot(Map.of(CALLBACK_SETTING, CALLBACK_ADDRESS)));
    }

    @Test
    void bootsWithoutIdentityDbSettingsAndCallbackUnset() {
        assertLocalPublisherExtensionNotLoaded(boot(Map.of()));
    }

    @Test
    void bootsWithIdentityDbSettingsAndCallbackSet() {
        assertLocalPublisherExtensionNotLoaded(boot(withIdentityDbSettings(Map.of(CALLBACK_SETTING, CALLBACK_ADDRESS))));
    }

    @Test
    void bootsWithIdentityDbSettingsAndCallbackUnset() {
        assertLocalPublisherExtensionNotLoaded(boot(withIdentityDbSettings(Map.of())));
    }

    private static Map<String, String> withIdentityDbSettings(Map<String, String> base) {
        var settings = new HashMap<>(base);
        settings.put(IDENTITY_DB_URL_SETTING, "jdbc:postgresql://identity-db.example:5432/centerui");
        settings.put(IDENTITY_DB_USERNAME_SETTING, "ds-issuer-service");
        settings.put(IDENTITY_DB_PASSWORD_SETTING, "unused");
        settings.put(IDENTITY_DB_SCHEMA_SETTING, "centerui");
        return settings;
    }

    private static List<ServiceExtension> boot(Map<String, String> overrides) {
        var settings = new HashMap<>(Map.of(VAULT_TOKEN_SETTING, VAULT_TOKEN));
        settings.putAll(overrides);
        return ExtensionScanGuard.initializeNonExcluded(ExtensionBootTest.class.getResource("/application.yaml"), settings, context -> {
            context.registerService(RpcCredentialsConfigurer.class, mock(RpcCredentialsConfigurer.class));
            context.registerService(GlobalConfProvider.class, mock(GlobalConfProvider.class));
            context.registerService(VaultClient.class, mock(VaultClient.class));
        });
    }

    private static void assertLocalPublisherExtensionNotLoaded(List<ServiceExtension> extensions) {
        assertThat(extensions).noneMatch(extension -> extension.getClass() == LocalStatusListCredentialPublisherExtension.class);
    }
}
