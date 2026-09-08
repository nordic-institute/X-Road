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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.niis.xroad.test.apitest.core.junit.Step;

import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.confproxy.test.container.ConfProxyIntTestContainerSetup.CONFIGURATION_PROXY;

/**
 * 0100 - Configuration Proxy: API key management via CLI. Generates and revokes API keys via the
 * confproxy CLI; the resulting key ids are asserted in ascending order, so scenarios run in this exact
 * order. Runs first in the suite because every later class needs an API key to authenticate its REST
 * calls.
 */
@Order(100)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConfProxyApiKeyManagementIntTest extends AbstractConfProxyIntTest {

    private static final Pattern ROLES_PATTERN = Pattern.compile("Roles:\\s+(\\S+)");

    @Test
    @Order(1)
    @DisplayName("Generating new API keys")
    void generatingNewApiKeys() {
        Step.given("there are no API keys", this::assertNoApiKeys);

        var firstKeyOutput = Step.when("new API key is generated via CLI", () -> execGenerateApiKeyCli().getStdout());
        var firstKey = parseApiKeyInfo(firstKeyOutput);
        Step.then("the generated key output contains API key value, ID and roles",
                () -> assertGeneratedKeyOutputContainsKeyIdAndRoles(firstKeyOutput));
        Step.and("API key list contains key with ID 1", () -> assertApiKeyListContains(1));
        Step.and("the key list output contains ID and roles but not API key value",
                () -> assertKeyListOutputContainsIdAndRolesButNotKeyValue(List.of(firstKey)));

        Step.when("new API key is generated via CLI", this::generateApiKeyViaCli);
        Step.then("API key list contains key with ID 1", () -> assertApiKeyListContains(1));
        Step.and("API key list contains key with ID 2", () -> assertApiKeyListContains(2));
    }

    @Test
    @Order(2)
    @DisplayName("Revoking API keys")
    void revokingApiKeys() {
        Step.when("API key with ID 1 is revoked via CLI", () -> revokeApiKeyViaCli(1));
        Step.then("API key list does not contain key with ID 1", () -> assertApiKeyListDoesNotContain(1));
        Step.and("API key list contains key with ID 2", () -> assertApiKeyListContains(2));
    }

    @Test
    @Order(3)
    @DisplayName("List configured proxy instances via REST API")
    void listConfiguredProxyInstancesViaRest() {
        var apiKey = Step.given("new API key is generated via CLI", this::generateApiKeyViaCli);
        var response = Step.when("proxy instances are listed via REST using last generated API key",
                () -> listInstancesViaRest(apiKey));
        Step.then("the instances response is successful", () -> {
            assertThat(response).isNotNull();
            assertThat(response.availableInstances()).isNotNull();
        });
    }

    private void assertNoApiKeys() {
        var result = containerSetup.execInContainer(CONFIGURATION_PROXY, "confproxy-list-api-keys");
        assertThat(result.getExitCode()).isZero();
        assertThat(result.getStdout()).contains("No API keys found");
    }

    private void assertGeneratedKeyOutputContainsKeyIdAndRoles(String output) {
        assertThat(API_KEY_PATTERN.matcher(output).find()).as("Generate output should contain API key value").isTrue();
        assertThat(API_KEY_ID_PATTERN.matcher(output).find()).as("Generate output should contain ID").isTrue();
        assertThat(ROLES_PATTERN.matcher(output).find()).as("Generate output should contain Roles").isTrue();
    }

    private void assertApiKeyListContains(long id) {
        var result = containerSetup.execInContainer(CONFIGURATION_PROXY, "confproxy-list-api-keys");
        assertThat(result.getExitCode()).isZero();
        assertThat(result.getStdout()).contains("API key ID: " + id);
    }

    private void assertApiKeyListDoesNotContain(long id) {
        var result = containerSetup.execInContainer(CONFIGURATION_PROXY, "confproxy-list-api-keys");
        assertThat(result.getExitCode()).isZero();
        assertThat(result.getStdout()).doesNotContain("API key ID: " + id);
    }

    private void assertKeyListOutputContainsIdAndRolesButNotKeyValue(List<ApiKeyInfo> generatedKeys) {
        var result = containerSetup.execInContainer(CONFIGURATION_PROXY, "confproxy-list-api-keys");
        assertThat(result.getExitCode()).isZero();
        var output = result.getStdout();

        assertThat(output).contains("API key ID:");
        assertThat(ROLES_PATTERN.matcher(output).find()).as("List output should contain Roles").isTrue();
        for (ApiKeyInfo info : generatedKeys) {
            assertThat(output).as("List output should not contain API key value %s", info.key()).doesNotContain(info.key());
        }
    }

    private void revokeApiKeyViaCli(long id) {
        var result = containerSetup.execInContainer(CONFIGURATION_PROXY, "confproxy-revoke-api-key", "-i", String.valueOf(id));
        assertThat(result.getExitCode()).isZero();
        assertThat(result.getStdout()).contains("API key revoked.");
    }
}
