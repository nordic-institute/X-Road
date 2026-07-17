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

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.niis.xroad.test.apitest.core.junit.Step;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.confproxy.test.container.ConfProxyIntTestContainerSetup.CONFIGURATION_PROXY;

/**
 * 0200 - Configuration Proxy: instance creation and configuration via CLI. Every scenario mutates the
 * same {@code TEST} proxy instance's signing-key state (0 -> 1 -> 2 -> 1 keys), so classes and methods
 * both run in strict ascending order - the next class in the suite ({@link ConfProxyRestApiIntTest})
 * continues from the state this class leaves the instance in.
 */
@Order(200)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Slf4j
class ConfProxyInstanceConfigurationIntTest extends AbstractConfProxyIntTest {

    private static final String INSTANCE = "TEST";
    private static final int DOWNLOAD_RETRY_ATTEMPTS = 5;
    private static final Duration DOWNLOAD_RETRY_DELAY = Duration.ofSeconds(5);

    private ApiKeyInfo apiKey;

    @BeforeEach
    void generateApiKey() {
        apiKey = Step.given("new API key is generated via CLI", this::generateApiKeyViaCli);
    }

    @Test
    @Order(1)
    @DisplayName("Create proxy instance via CLI")
    void createProxyInstanceViaCli() {
        var result = Step.when("proxy instance \"TEST\" is created via CLI",
                () -> containerSetup.execInContainer(CONFIGURATION_PROXY, "confproxy-create-instance", "-p", INSTANCE));
        Step.then("the CLI output contains \"Done.\"", () -> {
            assertThat(result.getExitCode()).isZero();
            assertThat(result.getStdout()).contains("Done.");
        });
        Step.and("proxy instance \"TEST\" is present in the instance list",
                () -> assertThat(listInstancesViaRest(apiKey).availableInstances()).contains(INSTANCE));
    }

    @Test
    @Order(2)
    @DisplayName("View proxy instance configuration via CLI")
    void viewProxyInstanceConfigurationViaCli() {
        var result = Step.when("configuration is viewed for proxy instance \"TEST\"",
                () -> containerSetup.execInContainer(CONFIGURATION_PROXY, "confproxy-view-conf", "-p", INSTANCE));
        Step.then("the CLI output contains \"Configuration for proxy 'TEST'\"", () -> {
            assertThat(result.getExitCode()).isZero();
            assertThat(result.getStdout()).contains("Configuration for proxy 'TEST'");
        });
        Step.and("the CLI output contains \"Validity interval:\"", () -> assertThat(result.getStdout()).contains("Validity interval:"));
    }

    @Test
    @Order(3)
    @DisplayName("Add signing key to proxy instance")
    void addSigningKeyToProxyInstance() {
        var result = Step.when("a signing key is generated for proxy instance \"TEST\" from token \"0\"",
                () -> containerSetup.execInContainer(CONFIGURATION_PROXY,
                        "confproxy-add-signing-key", "-p", INSTANCE, "-t", "0", "--active-key"));
        Step.then("the CLI output contains \"Generated key with ID\"", () -> {
            assertThat(result.getExitCode()).isZero();
            assertThat(result.getStdout()).contains("Generated key with ID");
        });
        Step.and("the CLI output contains \"Saved self-signed certificate\"",
                () -> assertThat(result.getStdout()).contains("Saved self-signed certificate"));
        Step.and("proxy instance \"TEST\" has 1 signing key",
                () -> assertThat(getInstanceViaRest(apiKey, INSTANCE).signingKeysAndCerts()).hasSize(1));
    }

    @Test
    @Order(4)
    @DisplayName("Add second signing key and activate it")
    void addSecondSigningKeyAndActivateIt() {
        var genResult = Step.when("a signing key is generated for proxy instance \"TEST\" from token \"0\"",
                () -> containerSetup.execInContainer(CONFIGURATION_PROXY,
                        "confproxy-add-signing-key", "-p", INSTANCE, "-t", "0", "--active-key"));
        assertThat(genResult.getExitCode()).isZero();
        Step.then("proxy instance \"TEST\" has 2 signing keys",
                () -> assertThat(getInstanceViaRest(apiKey, INSTANCE).signingKeysAndCerts()).hasSize(2));

        var secondKeyId = getInstanceViaRest(apiKey, INSTANCE).signingKeysAndCerts().get(1).key();
        var activateResult = Step.when("the second signing key of proxy instance \"TEST\" is activated", () ->
                containerSetup.execInContainer(CONFIGURATION_PROXY, "confproxy-activate-signing-key", "-p", INSTANCE, "-k", secondKeyId));
        Step.then("the CLI output contains \"marked as active signing key\"", () -> {
            assertThat(activateResult.getExitCode()).isZero();
            assertThat(activateResult.getStdout()).contains("marked as active signing key");
        });
    }

    @Test
    @Order(5)
    @DisplayName("Delete non-active signing key")
    void deleteNonActiveSigningKey() {
        var nonActiveKey = getInstanceViaRest(apiKey, INSTANCE).signingKeysAndCerts().stream()
                .filter(kc -> !kc.active())
                .findFirst()
                .orElseThrow(() -> new AssertionError("No non-active key found to delete"));

        var result = Step.when("the first signing key of proxy instance \"TEST\" is deleted", () ->
                containerSetup.execInContainer(CONFIGURATION_PROXY, "confproxy-del-signing-key", "-p", INSTANCE, "-k", nonActiveKey.key()));
        Step.then("the CLI output contains \"Deleted key from 'conf.ini'.\"", () -> {
            assertThat(result.getExitCode()).isZero();
            assertThat(result.getStdout()).contains("Deleted key from 'conf.ini'.");
        });
        Step.and("proxy instance \"TEST\" has 1 signing key",
                () -> assertThat(getInstanceViaRest(apiKey, INSTANCE).signingKeysAndCerts()).hasSize(1));
    }

    @Test
    @Order(6)
    @DisplayName("Generate anchor and download configuration")
    void generateAnchorAndDownloadConfiguration() {
        var destPath = "/etc/xroad/confproxy/" + INSTANCE + "/anchor.xml";
        var copyResult = Step.given("source anchor \"/home/xroad/anchors/DEV_anchor.xml\" is provisioned for proxy instance \"TEST\"", () ->
                containerSetup.execInContainer(CONFIGURATION_PROXY, "cp", "/home/xroad/anchors/DEV_anchor.xml", destPath));
        assertThat(copyResult.getExitCode()).isZero();

        var anchorFile = "/tmp/anchor_TEST.xml";
        var generateResult = Step.and("anchor is generated for proxy instance \"TEST\" to file \"/tmp/anchor_TEST.xml\"", () ->
                containerSetup.execInContainer(CONFIGURATION_PROXY, "confproxy-generate-anchor", "-p", INSTANCE, "-f", anchorFile));
        Step.then("the CLI output contains \"Generated anchor xml to '/tmp/anchor_TEST.xml'\"", () -> {
            assertThat(generateResult.getExitCode()).isZero();
            var output = generateResult.getStdout() + generateResult.getStderr();
            assertThat(output).contains("Generated anchor xml to '/tmp/anchor_TEST.xml'");
        });

        Step.when("configuration is downloaded using anchor \"/tmp/anchor_TEST.xml\"", () -> downloadConfigurationWithRetry(anchorFile));
    }

    /**
     * The scheduled update job must publish the anchor's configuration before {@code confproxy-download-conf}
     * can fetch it, so download attempts are retried until the job has run.
     */
    private void downloadConfigurationWithRetry(String anchorFile) {
        var destination = "/tmp/conf_download_test";
        for (int attempt = 1; attempt <= DOWNLOAD_RETRY_ATTEMPTS; attempt++) {
            var result = containerSetup.execInContainer(CONFIGURATION_PROXY,
                    "confproxy-download-conf", "-a", anchorFile, "-d", destination);
            var output = result.getStdout() + result.getStderr();

            if (output.contains("Successfully downloaded configuration to:")) {
                return;
            }
            if (attempt < DOWNLOAD_RETRY_ATTEMPTS) {
                log.info("Download not yet available, waiting for update job to publish configuration...");
                sleep(DOWNLOAD_RETRY_DELAY);
            } else {
                assertThat(output).contains("Successfully downloaded configuration to:");
            }
        }
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Retry wait interrupted", e);
        }
    }
}
