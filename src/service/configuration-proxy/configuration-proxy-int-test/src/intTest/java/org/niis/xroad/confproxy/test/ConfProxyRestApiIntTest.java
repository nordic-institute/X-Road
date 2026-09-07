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
package org.niis.xroad.confproxy.test;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.niis.xroad.test.apitest.core.junit.Step;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 0300 - Configuration Proxy: instance management via REST API. Continues manipulating the {@code TEST}
 * instance {@link ConfProxyInstanceConfigurationIntTest} created and left with 1 signing key - scenarios
 * mutate the same signing-key state (1 -> 2 -> 1 again) via the REST API, so methods run in strict
 * ascending order.
 */
@Order(300)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConfProxyRestApiIntTest extends AbstractConfProxyIntTest {

    private static final String INSTANCE = "TEST";

    private ApiKeyInfo apiKey;

    @BeforeEach
    void generateApiKey() {
        apiKey = Step.given("new API key is generated via CLI", this::generateApiKeyViaCli);
    }

    @Test
    @Order(1)
    @DisplayName("List configured instances via REST API")
    void listConfiguredInstancesViaRest() {
        var response = Step.when("configured instances are listed via REST API", () -> listInstancesViaRest(apiKey));
        Step.then("the REST response contains instance \"TEST\"", () -> assertThat(response.availableInstances()).contains(INSTANCE));
    }

    @Test
    @Order(2)
    @DisplayName("Get instance details via REST API")
    void getInstanceDetailsViaRest() {
        var response = Step.when("instance \"TEST\" details are retrieved via REST API", () -> getInstanceViaRest(apiKey, INSTANCE));
        Step.then("the REST response instance name is \"TEST\"", () -> assertThat(response.name()).isEqualTo(INSTANCE));
        Step.and("the REST response instance has 1 signing key", () -> assertThat(response.signingKeysAndCerts()).hasSize(1));
        Step.and("the REST response instance is configured", () -> assertThat(response.configured()).isTrue());
    }

    @Test
    @Order(3)
    @DisplayName("Add signing key via REST API")
    void addSigningKeyViaRestApi() {
        var request = new AddSigningKeyRequest(null, "0", "RSA", false);
        var response = Step.when("a signing key is added to instance \"TEST\" from token \"0\" via REST API",
                () -> addSigningKeyViaRest(apiKey, INSTANCE, request));
        Step.then("the REST response instance has 2 signing keys", () -> assertThat(response.signingKeysAndCerts()).hasSize(2));
    }

    @Test
    @Order(4)
    @DisplayName("Activate signing key via REST API")
    void activateSigningKeyViaRestApi() {
        var keyId = getInstanceViaRest(apiKey, INSTANCE).signingKeysAndCerts().get(1).key();

        var response = Step.when("the second signing key of instance \"TEST\" is activated via REST API",
                () -> setActiveSigningKeyViaRest(apiKey, INSTANCE, keyId));
        Step.then("the REST response instance has 2 signing keys", () -> assertThat(response.signingKeysAndCerts()).hasSize(2));
        Step.and("the second signing key of the REST response is active",
                () -> assertThat(response.signingKeysAndCerts().get(1).active()).isTrue());
    }

    @Test
    @Order(5)
    @DisplayName("Delete non-active signing key via REST API")
    void deleteNonActiveSigningKeyViaRestApi() {
        var nonActiveKey = getInstanceViaRest(apiKey, INSTANCE).signingKeysAndCerts().stream()
                .filter(kc -> !kc.active())
                .findFirst()
                .orElseThrow(() -> new AssertionError("No non-active key found to delete"));

        var response = Step.when("the non-active signing key of instance \"TEST\" is deleted via REST API",
                () -> removeSigningKeyViaRest(apiKey, INSTANCE, nonActiveKey.key()));
        Step.then("the REST response instance has 1 signing key", () -> assertThat(response.signingKeysAndCerts()).hasSize(1));
    }

    @Test
    @Order(6)
    @DisplayName("Generate anchor via REST API")
    void generateAnchorViaRestApi() {
        var anchorBytes = Step.when("anchor is generated for instance \"TEST\" via REST API",
                () -> generateAnchorViaRest(apiKey, INSTANCE));
        Step.then("the REST response contains a valid anchor XML", () -> {
            assertThat(anchorBytes).isNotNull().isNotEmpty();
            var anchorXml = new String(anchorBytes, StandardCharsets.UTF_8);
            assertThat(anchorXml).contains("configurationAnchor");
            assertThat(anchorXml).contains("<instanceIdentifier>");
        });
    }
}
