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
package org.niis.xroad.confproxy.test.glue;

import io.cucumber.java.en.Step;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.confproxy.test.ConfProxyIntTestContainerSetup;
import org.niis.xroad.confproxy.test.container.ConfProxyIntTestConfiguration;
import org.niis.xroad.confproxy.test.container.ConfProxyIntTestConfiguration.InstancesResponse;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class ConfProxyInstanceStepDefs extends BaseConfProxyStepDefs {

    private static final Pattern API_KEY_PATTERN = Pattern.compile("API key:\\s+(\\S+)");
    private static final Pattern API_KEY_ID_PATTERN = Pattern.compile("ID:\\s+(\\d+)");
    private static final Pattern ROLES_PATTERN = Pattern.compile("Roles:\\s+(\\S+)");

    @Autowired
    private ConfProxyIntTestConfiguration.FeignConfProxyApi confProxyApi;

    @Step("there are no API keys")
    public void thereAreNoApiKeys() throws Exception {
        var result = confProxySetup.execInContainer(
                ConfProxyIntTestContainerSetup.CONFIGURATION_PROXY,
                "confproxy-list-api-keys");

        log.info("CLI list-api-keys output: {}", result.getStdout());
        assertThat(result.getExitCode()).isZero();
        assertThat(result.getStdout()).contains("No API keys found");
    }

    @Step("new API key is generated via CLI")
    public void generateApiKey() throws Exception {
        var result = confProxySetup.execInContainer(
                ConfProxyIntTestContainerSetup.CONFIGURATION_PROXY,
                "confproxy-generate-api-key", "-r", "SYSTEM_ADMINISTRATOR");

        log.info("CLI generate-api-key output: {}", result.getStdout());
        assertThat(result.getExitCode()).isZero();

        Matcher keyMatcher = API_KEY_PATTERN.matcher(result.getStdout());
        assertThat(keyMatcher.find()).as("API key value should be present in CLI output").isTrue();
        String apiKey = keyMatcher.group(1);

        Matcher idMatcher = API_KEY_ID_PATTERN.matcher(result.getStdout());
        assertThat(idMatcher.find()).as("API key ID should be present in CLI output").isTrue();
        long keyId = Long.parseLong(idMatcher.group(1));

        var info = new ApiKeyInfo(apiKey, keyId);
        getApiKeyMap().put(keyId, info);
        putStepData(StepDataKey.LAST_GENERATED_KEY, info);
        putStepData(StepDataKey.LAST_CLI_OUTPUT, result.getStdout());
        testReportService.attachText("Generated API key", "key=" + apiKey + ", id=" + keyId);
    }

    @Step("the generated key output contains API key value, ID and roles")
    public void generatedKeyOutputContainsKeyIdAndRoles() {
        String output = (String) getStepData(StepDataKey.LAST_CLI_OUTPUT).orElseThrow();

        assertThat(API_KEY_PATTERN.matcher(output).find())
                .as("Generate output should contain API key value").isTrue();
        assertThat(API_KEY_ID_PATTERN.matcher(output).find())
                .as("Generate output should contain ID").isTrue();
        assertThat(ROLES_PATTERN.matcher(output).find())
                .as("Generate output should contain Roles").isTrue();
    }

    @Step("API key list contains key with ID {long}")
    public void apiKeyListContainsKeyWithId(long id) throws Exception {
        var result = confProxySetup.execInContainer(
                ConfProxyIntTestContainerSetup.CONFIGURATION_PROXY,
                "confproxy-list-api-keys");

        log.info("CLI list-api-keys output: {}", result.getStdout());
        assertThat(result.getExitCode()).isZero();
        assertThat(result.getStdout()).contains("API key ID: " + id);
        putStepData(StepDataKey.LAST_CLI_OUTPUT, result.getStdout());
    }

    @Step("the key list output contains ID and roles but not API key value")
    public void keyListOutputContainsIdAndRolesButNotKeyValue() {
        String output = (String) getStepData(StepDataKey.LAST_CLI_OUTPUT).orElseThrow();

        assertThat(output).contains("API key ID:");
        assertThat(ROLES_PATTERN.matcher(output).find())
                .as("List output should contain Roles").isTrue();

        for (ApiKeyInfo info : getApiKeyMap().values()) {
            assertThat(output).as("List output should not contain API key value %s", info.key())
                    .doesNotContain(info.key());
        }
    }

    @Step("API key list does not contain key with ID {long}")
    public void apiKeyListDoesNotContainKeyWithId(long id) throws Exception {
        var result = confProxySetup.execInContainer(
                ConfProxyIntTestContainerSetup.CONFIGURATION_PROXY,
                "confproxy-list-api-keys");

        log.info("CLI list-api-keys output: {}", result.getStdout());
        assertThat(result.getExitCode()).isZero();
        assertThat(result.getStdout()).doesNotContain("API key ID: " + id);
    }

    @Step("API key with ID {long} is revoked via CLI")
    public void revokeApiKeyWithId(long id) throws Exception {
        var result = confProxySetup.execInContainer(
                ConfProxyIntTestContainerSetup.CONFIGURATION_PROXY,
                "confproxy-revoke-api-key", "-i", String.valueOf(id));

        log.info("CLI revoke-api-key output: {}", result.getStdout());
        assertThat(result.getExitCode()).isZero();
        assertThat(result.getStdout()).contains("API key revoked.");
    }

    @Step("proxy instances are listed via REST using last generated API key")
    public void listInstancesViaRest() {
        ApiKeyInfo info = (ApiKeyInfo) getStepData(StepDataKey.LAST_GENERATED_KEY).orElseThrow();

        String authHeader = "X-Road-ApiKey token=" + info.key();
        InstancesResponse response = confProxyApi.getInstances(authHeader);
        testReportService.attachJson("Instances response", response);
        putStepData(StepDataKey.INSTANCES_RESPONSE, response);

        log.info("Listed instances via REST: {}", response.availableInstances());
    }

    @Step("the instances response is successful")
    public void instancesResponseIsSuccessful() {
        InstancesResponse response = (InstancesResponse) getStepData(StepDataKey.INSTANCES_RESPONSE).orElseThrow();
        assertThat(response).isNotNull();
        assertThat(response.availableInstances()).isNotNull();
    }
}
