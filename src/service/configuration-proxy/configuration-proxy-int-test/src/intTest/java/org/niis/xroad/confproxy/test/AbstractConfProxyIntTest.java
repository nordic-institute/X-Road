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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.niis.xroad.confproxy.test.container.ConfProxyIntTestContainerSetup;
import org.niis.xroad.test.apitest.core.junit.ApiStackExtension;
import org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory;
import org.testcontainers.containers.Container;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.confproxy.test.container.ConfProxyIntTestContainerSetup.CONFIGURATION_PROXY;

/**
 * Shared fixture helpers for every configuration-proxy scenario class: CLI-driven API key generation
 * against the shared {@link ConfProxyIntTestContainerSetup} stack, and RestAssured calls to the
 * configuration-proxy REST API - a like-for-like replacement for the legacy Feign {@code FeignConfProxyApi}.
 */
@ExtendWith(ApiStackExtension.class)
abstract class AbstractConfProxyIntTest {

    private static final int HTTP_OK = 200;
    protected static final Pattern API_KEY_PATTERN = Pattern.compile("API key:\\s+(\\S+)");
    protected static final Pattern API_KEY_ID_PATTERN = Pattern.compile("ID:\\s+(\\d+)");

    protected ConfProxyIntTestContainerSetup containerSetup;

    @BeforeEach
    final void injectContainerSetup(ConfProxyIntTestContainerSetup setup) {
        this.containerSetup = setup;
    }

    protected record ApiKeyInfo(String key, long id) {
    }

    /**
     * Generates a new API key via the CLI and returns the raw exec result, so callers that need to
     * assert on the CLI output text (id/roles formatting) can do so before parsing it.
     */
    protected Container.ExecResult execGenerateApiKeyCli() {
        var result = containerSetup.execInContainer(CONFIGURATION_PROXY, "confproxy-generate-api-key", "-r", "SYSTEM_ADMINISTRATOR");
        assertThat(result.getExitCode()).isZero();
        return result;
    }

    protected ApiKeyInfo generateApiKeyViaCli() {
        return parseApiKeyInfo(execGenerateApiKeyCli().getStdout());
    }

    protected ApiKeyInfo parseApiKeyInfo(String cliOutput) {
        Matcher keyMatcher = API_KEY_PATTERN.matcher(cliOutput);
        assertThat(keyMatcher.find()).as("API key value should be present in CLI output").isTrue();
        Matcher idMatcher = API_KEY_ID_PATTERN.matcher(cliOutput);
        assertThat(idMatcher.find()).as("API key ID should be present in CLI output").isTrue();
        return new ApiKeyInfo(keyMatcher.group(1), Long.parseLong(idMatcher.group(1)));
    }

    protected String authHeader(ApiKeyInfo apiKey) {
        return "X-Road-ApiKey token=" + apiKey.key();
    }

    protected InstancesResponse listInstancesViaRest(ApiKeyInfo apiKey) {
        return RestAssuredFactory.given()
                .header("Authorization", authHeader(apiKey))
                .get(containerSetup.apiBaseUrl() + "/v1/instances")
                .then().statusCode(HTTP_OK)
                .extract().as(InstancesResponse.class);
    }

    protected InstanceResponse getInstanceViaRest(ApiKeyInfo apiKey, String name) {
        return RestAssuredFactory.given()
                .header("Authorization", authHeader(apiKey))
                .get(containerSetup.apiBaseUrl() + "/v1/instances/" + name)
                .then().statusCode(HTTP_OK)
                .extract().as(InstanceResponse.class);
    }

    protected InstanceResponse addSigningKeyViaRest(ApiKeyInfo apiKey, String name, AddSigningKeyRequest request) {
        return RestAssuredFactory.given()
                .header("Authorization", authHeader(apiKey))
                .contentType(ContentType.JSON)
                .body(request)
                .post(containerSetup.apiBaseUrl() + "/v1/instances/" + name + "/signing-key")
                .then().statusCode(HTTP_OK)
                .extract().as(InstanceResponse.class);
    }

    protected InstanceResponse setActiveSigningKeyViaRest(ApiKeyInfo apiKey, String name, String keyId) {
        return RestAssuredFactory.given()
                .header("Authorization", authHeader(apiKey))
                .patch(containerSetup.apiBaseUrl() + "/v1/instances/" + name + "/signing-key/" + keyId + "/set-active")
                .then().statusCode(HTTP_OK)
                .extract().as(InstanceResponse.class);
    }

    protected InstanceResponse removeSigningKeyViaRest(ApiKeyInfo apiKey, String name, String keyId) {
        return RestAssuredFactory.given()
                .header("Authorization", authHeader(apiKey))
                .delete(containerSetup.apiBaseUrl() + "/v1/instances/" + name + "/signing-key/" + keyId)
                .then().statusCode(HTTP_OK)
                .extract().as(InstanceResponse.class);
    }

    protected byte[] generateAnchorViaRest(ApiKeyInfo apiKey, String name) {
        return RestAssuredFactory.given()
                .header("Authorization", authHeader(apiKey))
                .get(containerSetup.apiBaseUrl() + "/v1/instances/" + name + "/anchor")
                .then().statusCode(HTTP_OK)
                .extract().asByteArray();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    protected record AddSigningKeyRequest(
            @JsonProperty("key_id")
            String keyId,
            @JsonProperty("token_id")
            String tokenId,
            @JsonProperty("key_algorithm")
            String keyAlgorithm,
            @JsonProperty("as_active")
            boolean asActive
    ) {
    }

    protected record InstancesResponse(
            @JsonProperty("available_instances")
            Set<String> availableInstances
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    protected record InstanceResponse(
            String name,
            @JsonProperty("configuration_path")
            String configurationPath,
            @JsonProperty("validity_interval")
            int validityInterval,
            @JsonProperty("signing_keys_and_certs")
            List<KeyCert> signingKeysAndCerts,
            boolean configured,
            Anchor anchor,
            @JsonProperty("anchor_error")
            String anchorError
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    protected record KeyCert(
            boolean active,
            String key,
            String cert
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    protected record Anchor(
            @JsonProperty("instance_identifier")
            String instanceIdentifier,
            @JsonProperty("generated_at")
            Instant generatedAt,
            String hash
    ) {
    }
}
