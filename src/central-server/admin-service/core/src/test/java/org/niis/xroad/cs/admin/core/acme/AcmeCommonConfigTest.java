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
package org.niis.xroad.cs.admin.core.acme;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the {@code xroad.acme} YAML binding tolerates a config block written for (or copied verbatim from) the
 * Security Server - same top-level key, same EAB map shape, but a superset of fields this product's trimmed
 * {@link AcmeProperties.Credentials} does not model (auth/sign-profile kid/mac-key pairs) plus a top-level
 * {@code contacts} map this product has no field for at all. Before {@code PropertyUtils.setSkipMissingProperties}
 * was wired in, SnakeYAML's strict bean binding threw on the first unknown property, and the blanket catch in
 * {@link AcmeCommonConfig#acmeProperties} silently swallowed that into an empty {@link AcmeProperties} - every
 * CA's EAB credentials gone, with only a log line to show for it.
 */
class AcmeCommonConfigTest {

    private final AcmeCommonConfig config = new AcmeCommonConfig();

    @Test
    void parsesSecurityServerStyleConfigWithExtraFieldsAndYieldsTheDataspaceTlsEabCredentials() {
        String yaml = """
                eab-credentials:
                  certificate-authorities:
                    test-ca:
                      mac-key-base64-encoded: true
                      members:
                        dataspace-tls:
                          kid: test-kid
                          mac-key: dGVzdC1tYWMta2V5
                          auth-kid: member-auth-kid
                          auth-mac-key: bWVtYmVyLWF1dGgtbWFjLWtleQ==
                          sign-kid: member-sign-kid
                          sign-mac-key: bWVtYmVyLXNpZ24tbWFjLWtleQ==
                account-keystore-password: test-keystore-password
                contacts:
                  admin: mailto:admin@example.org
                """;

        AcmeProperties properties = config.acmeProperties(yaml);

        AcmeProperties.Credentials dsTlsCredentials = properties.getEabCredentials("test-ca", "dataspace-tls");
        assertThat(dsTlsCredentials.getKid()).isEqualTo("test-kid");
        assertThat(dsTlsCredentials.getMacKey()).isEqualTo("dGVzdC1tYWMta2V5");
        assertThat(properties.isEabMacKeyBase64Encoded("test-ca")).isTrue();
        assertThat(new String(properties.getAccountKeystorePassword())).isEqualTo("test-keystore-password");
    }

    @Test
    void fallsBackToEmptyPropertiesOnMalformedYaml() {
        String malformedYaml = "eab-credentials: [this is not a mapping: - broken";

        AcmeProperties properties = config.acmeProperties(malformedYaml);

        assertThat(properties.getAccountKeystorePassword()).isNull();
        assertThat(properties.hasEabCredentials("any-ca", "any-alias")).isFalse();
    }

    @Test
    void returnsEmptyPropertiesWhenConfigurationIsBlank() {
        AcmeProperties properties = config.acmeProperties(" ");

        assertThat(properties.getAccountKeystorePassword()).isNull();
        assertThat(properties.hasEabCredentials("any-ca", "any-alias")).isFalse();
    }
}
