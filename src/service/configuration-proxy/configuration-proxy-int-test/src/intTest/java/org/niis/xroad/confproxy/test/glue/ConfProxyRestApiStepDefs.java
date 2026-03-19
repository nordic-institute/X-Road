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
import org.niis.xroad.confproxy.test.container.ConfProxyIntTestConfiguration;
import org.niis.xroad.confproxy.test.container.ConfProxyIntTestConfiguration.AddSigningKeyRequest;
import org.niis.xroad.confproxy.test.container.ConfProxyIntTestConfiguration.InstanceResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class ConfProxyRestApiStepDefs extends BaseConfProxyStepDefs {

    private static final String LAST_REST_RESPONSE = "LAST_REST_RESPONSE";
    private static final String LAST_ANCHOR_BYTES = "LAST_ANCHOR_BYTES";

    @Autowired
    private ConfProxyIntTestConfiguration.FeignConfProxyApi confProxyApi;

    @Step("configured instances are listed via REST API")
    public void listInstancesViaRest() {
        var response = confProxyApi.getInstances(authHeader());
        testReportService.attachJson("Instances list", response);
        scenarioContext.putStepData(LAST_REST_RESPONSE, response);
    }

    @Step("the REST response contains instance {string}")
    public void restResponseContainsInstance(String instanceName) {
        ConfProxyIntTestConfiguration.InstancesResponse response = scenarioContext.getStepData(LAST_REST_RESPONSE);
        assertThat(response.availableInstances()).contains(instanceName);
    }

    @Step("instance {string} details are retrieved via REST API")
    public void getInstanceDetails(String instanceName) {
        var response = confProxyApi.getInstance(instanceName, authHeader());
        testReportService.attachJson("Instance " + instanceName, response);
        scenarioContext.putStepData(LAST_REST_RESPONSE, response);
    }

    @Step("the REST response instance name is {string}")
    public void restResponseInstanceNameIs(String expectedName) {
        InstanceResponse response = scenarioContext.getStepData(LAST_REST_RESPONSE);
        assertThat(response.name()).isEqualTo(expectedName);
    }

    @Step("the REST response instance has {int} signing key(s)")
    public void restResponseHasSigningKeys(int expectedCount) {
        InstanceResponse response = scenarioContext.getStepData(LAST_REST_RESPONSE);
        assertThat(response.signingKeysAndCerts())
                .as("Instance should have %d signing key(s)", expectedCount)
                .hasSize(expectedCount);
    }

    @Step("the REST response instance is configured")
    public void restResponseInstanceIsConfigured() {
        InstanceResponse response = scenarioContext.getStepData(LAST_REST_RESPONSE);
        assertThat(response.configured()).isTrue();
    }

    @Step("a signing key is added to instance {string} from token {string} via REST API")
    public void addSigningKeyViaRest(String instanceName, String tokenId) {
        var request = new AddSigningKeyRequest(null, tokenId, "RSA", false);
        var response = confProxyApi.addSigningKey(instanceName, request, authHeader());
        testReportService.attachJson("Add signing key to " + instanceName, response);
        scenarioContext.putStepData(LAST_REST_RESPONSE, response);
    }

    @Step("the second signing key of instance {string} is activated via REST API")
    public void activateSecondSigningKeyViaRest(String instanceName) {
        InstanceResponse current = confProxyApi.getInstance(instanceName, authHeader());
        assertThat(current.signingKeysAndCerts()).hasSizeGreaterThanOrEqualTo(2);

        String keyId = current.signingKeysAndCerts().get(1).key();
        var response = confProxyApi.setActiveSigningKey(instanceName, keyId, authHeader());
        testReportService.attachJson("Activate key " + keyId, response);
        scenarioContext.putStepData(LAST_REST_RESPONSE, response);
    }

    @Step("the second signing key of the REST response is active")
    public void secondSigningKeyIsActive() {
        InstanceResponse response = scenarioContext.getStepData(LAST_REST_RESPONSE);
        assertThat(response.signingKeysAndCerts()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(response.signingKeysAndCerts().get(1).active()).isTrue();
    }

    @Step("the non-active signing key of instance {string} is deleted via REST API")
    public void deleteNonActiveSigningKeyViaRest(String instanceName) {
        InstanceResponse current = confProxyApi.getInstance(instanceName, authHeader());

        var nonActiveKey = current.signingKeysAndCerts().stream()
                .filter(kc -> !kc.active())
                .findFirst()
                .orElseThrow(() -> new AssertionError("No non-active key found to delete"));

        var response = confProxyApi.removeSigningKey(instanceName, nonActiveKey.key(), authHeader());
        testReportService.attachJson("Delete key " + nonActiveKey.key(), response);
        scenarioContext.putStepData(LAST_REST_RESPONSE, response);
    }

    @Step("anchor is generated for instance {string} via REST API")
    public void generateAnchorViaRest(String instanceName) throws IOException {
        var response = confProxyApi.generateAnchor(instanceName, authHeader());
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        Resource resource = response.getBody();
        assertThat(resource).isNotNull();
        scenarioContext.putStepData(LAST_ANCHOR_BYTES, resource.getContentAsByteArray());
    }

    @Step("the REST response contains a valid anchor XML")
    public void restResponseContainsValidAnchorXml() {
        byte[] anchorBytes = scenarioContext.getStepData(LAST_ANCHOR_BYTES);
        assertThat(anchorBytes).isNotNull().isNotEmpty();

        String anchorXml = new String(anchorBytes);
        assertThat(anchorXml).contains("configurationAnchor");
        assertThat(anchorXml).contains("<instanceIdentifier>");
    }

    private String authHeader() {
        ApiKeyInfo apiKey = (ApiKeyInfo) getStepData(StepDataKey.LAST_GENERATED_KEY).orElseThrow();
        return "X-Road-ApiKey token=" + apiKey.key();
    }
}
