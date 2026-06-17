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
import org.niis.xroad.confproxy.test.container.ConfProxyIntTestConfiguration.InstanceResponse;
import org.niis.xroad.confproxy.test.container.ConfProxyIntTestConfiguration.KeyCert;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class ConfProxyInstanceConfigStepDefs extends BaseConfProxyStepDefs {

    public static final int SECONDS_5 = 5_000;
    @Autowired
    private ConfProxyIntTestConfiguration.FeignConfProxyApi confProxyApi;

    @Step("proxy instance {string} is created via CLI")
    public void createProxyInstance(String instanceName) throws Exception {
        var result = confProxySetup.execInContainer(
                ConfProxyIntTestContainerSetup.CONFIGURATION_PROXY,
                "confproxy-create-instance", "-p", instanceName);

        assertThat(result.getExitCode()).isZero();
        putStepData(StepDataKey.LAST_CLI_OUTPUT, result.getStdout());
    }

    @Step("the CLI output contains {string}")
    public void cliOutputContains(String expectedText) {
        String output = (String) getStepData(StepDataKey.LAST_CLI_OUTPUT).orElseThrow();
        assertThat(output).contains(expectedText);
    }

    @Step("proxy instance {string} is present in the instance list")
    public void instanceIsPresentInList(String instanceName) {
        ApiKeyInfo apiKey = (ApiKeyInfo) getStepData(StepDataKey.LAST_GENERATED_KEY).orElseThrow();
        String authHeader = "X-Road-ApiKey token=" + apiKey.key();

        var response = confProxyApi.getInstances(authHeader);
        assertThat(response.availableInstances()).contains(instanceName);
    }

    @Step("configuration is viewed for proxy instance {string}")
    public void viewInstanceConfiguration(String instanceName) throws Exception {
        var result = confProxySetup.execInContainer(
                ConfProxyIntTestContainerSetup.CONFIGURATION_PROXY,
                "confproxy-view-conf", "-p", instanceName);

        assertThat(result.getExitCode()).isZero();
        putStepData(StepDataKey.LAST_CLI_OUTPUT, result.getStdout());
    }

    @Step("a signing key is generated for proxy instance {string} from token {string}")
    public void generateSigningKey(String instanceName, String tokenId) throws Exception {
        var result = confProxySetup.execInContainer(
                ConfProxyIntTestContainerSetup.CONFIGURATION_PROXY,
                "confproxy-add-signing-key", "-p", instanceName, "-t", tokenId, "--active-key");

        assertThat(result.getExitCode()).isZero();
        putStepData(StepDataKey.LAST_CLI_OUTPUT, result.getStdout());
    }

    @Step("proxy instance {string} has {int} signing key(s)")
    public void instanceHasSigningKeys(String instanceName, int expectedCount) {
        InstanceResponse instance = getInstanceViaRest(instanceName);

        assertThat(instance.signingKeysAndCerts())
                .as("Instance '%s' should have %d signing key(s)", instanceName, expectedCount)
                .hasSize(expectedCount);
    }

    @Step("the second signing key of proxy instance {string} is activated")
    public void activateSecondSigningKey(String instanceName) throws Exception {
        InstanceResponse instance = getInstanceViaRest(instanceName);
        assertThat(instance.signingKeysAndCerts()).hasSizeGreaterThanOrEqualTo(2);

        String keyId = instance.signingKeysAndCerts().get(1).key();

        var result = confProxySetup.execInContainer(
                ConfProxyIntTestContainerSetup.CONFIGURATION_PROXY,
                "confproxy-activate-signing-key", "-p", instanceName, "-k", keyId);

        assertThat(result.getExitCode()).isZero();
        putStepData(StepDataKey.LAST_CLI_OUTPUT, result.getStdout());
    }

    @Step("the first signing key of proxy instance {string} is deleted")
    public void deleteFirstSigningKey(String instanceName) throws Exception {
        InstanceResponse instance = getInstanceViaRest(instanceName);
        assertThat(instance.signingKeysAndCerts()).isNotEmpty();

        KeyCert firstKey = instance.signingKeysAndCerts().stream()
                .filter(kc -> !kc.active())
                .findFirst()
                .orElseThrow(() -> new AssertionError("No non-active key found to delete"));


        var result = confProxySetup.execInContainer(
                ConfProxyIntTestContainerSetup.CONFIGURATION_PROXY,
                "confproxy-del-signing-key", "-p", instanceName, "-k", firstKey.key());

        assertThat(result.getExitCode()).isZero();
        putStepData(StepDataKey.LAST_CLI_OUTPUT, result.getStdout());
    }

    @Step("source anchor {string} is provisioned for proxy instance {string}")
    public void provisionSourceAnchor(String anchorFile, String instanceName) throws Exception {
        var destPath = "/etc/xroad/confproxy/" + instanceName + "/anchor.xml";

        var result = confProxySetup.execInContainer(
                ConfProxyIntTestContainerSetup.CONFIGURATION_PROXY,
                "cp", anchorFile, destPath);
        assertThat(result.getExitCode()).isZero();
    }

    @Step("anchor is generated for proxy instance {string} to file {string}")
    public void generateAnchor(String instanceName, String filename) throws Exception {
        var result = confProxySetup.execInContainer(
                ConfProxyIntTestContainerSetup.CONFIGURATION_PROXY,
                "confproxy-generate-anchor", "-p", instanceName, "-f", filename);

        assertThat(result.getExitCode()).isZero();
        String output = result.getStdout() + result.getStderr();
        putStepData(StepDataKey.LAST_CLI_OUTPUT, output);
    }

    @Step("configuration is downloaded using anchor {string}")
    public void downloadConfigurationUsingAnchor(String anchorFile) throws Exception {
        var destination = "/tmp/conf_download_test";

        // The update job must publish config before download-conf can fetch it.
        // Retry to allow time for the scheduled update job to run.
        final int maxAttempts = 5;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            var result = confProxySetup.execInContainer(
                    ConfProxyIntTestContainerSetup.CONFIGURATION_PROXY,
                    "confproxy-download-conf", "-a", anchorFile, "-d", destination);

            String output = result.getStdout() + result.getStderr();

            if (output.contains("Successfully downloaded configuration to:")) {
                putStepData(StepDataKey.LAST_CLI_OUTPUT, output);
                return;
            }

            if (attempt < maxAttempts) {
                log.info("Download not yet available, waiting for update job to publish configuration...");
                Thread.sleep(SECONDS_5);
            } else {
                putStepData(StepDataKey.LAST_CLI_OUTPUT, output);
                assertThat(output).contains("Successfully downloaded configuration to:");
            }
        }
    }

    private InstanceResponse getInstanceViaRest(String instanceName) {
        ApiKeyInfo apiKey = (ApiKeyInfo) getStepData(StepDataKey.LAST_GENERATED_KEY).orElseThrow();
        String authHeader = "X-Road-ApiKey token=" + apiKey.key();

        InstanceResponse response = confProxyApi.getInstance(instanceName, authHeader);
        testReportService.attachJson("Instance " + instanceName, response);
        return response;
    }
}
