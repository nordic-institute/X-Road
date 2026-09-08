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
package org.niis.xroad.ds.controlplane;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.source.yaml.YamlConfigSource;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guards the packaged {@code application.yaml} declaration that lets an X-Road key carry an EDC setting.
 *
 * <p>{@code edc.iam.trusted-issuer.issuer.id} is read by the EDC runtime through
 * {@code QuarkusConfigBridge}, which snapshots {@code config.getPropertyNames()}. An env var cannot
 * introduce that key — SmallRye enumerates env vars with dots in place of every underscore, so a
 * hyphenated key never matches — hence the packaged yaml declares it and interpolates
 * {@code xroad.edc.iam.trusted-issuer.issuer.id}, which any source may then supply.
 *
 * <p>{@code addDefaultInterceptors()} is what turns on {@code ${...}} expansion, as Quarkus does at
 * runtime; without it SmallRye hands back the literal expression.
 *
 * <p>The declaration is easy to lose silently: a second {@code edc.iam} mapping elsewhere in the file
 * would shadow it (YAML keeps the last duplicate key) and nothing would fail until a deployment could
 * not validate credentials.
 */
class PackagedEdcConfigTest {

    private static final String EDC_KEY = "edc.iam.trusted-issuer.issuer.id";
    private static final String XROAD_KEY = "xroad.edc.iam.trusted-issuer.issuer.id";

    @Test
    void packagedYamlDeclaresTheEdcKeyAndTakesItsValueFromTheXroadKey() throws Exception {
        var config = new SmallRyeConfigBuilder()
                .addDefaultInterceptors()
                .withSources(packagedYaml())
                .withSources(new PropertiesConfigSource(Map.of(XROAD_KEY, "did:web:issuer.example"), "test", 300))
                .build();

        assertThat(config.getPropertyNames()).contains(EDC_KEY);
        assertThat(config.getValue(EDC_KEY, String.class)).isEqualTo("did:web:issuer.example");
    }

    @Test
    void unsetXroadKeyFailsNamingBothProperties() throws Exception {
        var config = new SmallRyeConfigBuilder().addDefaultInterceptors().withSources(packagedYaml()).build();

        assertThatThrownBy(() -> config.getValue(EDC_KEY, String.class))
                .as("no fallback on purpose: an empty DID would register a trusted issuer that rejects "
                        + "every credential, so an unset value has to surface at startup")
                .hasMessageContaining(XROAD_KEY);
    }

    private static YamlConfigSource packagedYaml() throws Exception {
        var url = PackagedEdcConfigTest.class.getResource("/application.yaml");
        assertThat(url).as("packaged application.yaml on the test classpath").isNotNull();
        return new YamlConfigSource(url);
    }
}
