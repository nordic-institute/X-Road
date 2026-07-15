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
package org.niis.xroad.signer.test;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.properties.NodeProperties;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.client.impl.SignerSignRpcClient;
import org.niis.xroad.signer.test.container.SignerIntTestContainerSetup;
import org.niis.xroad.test.apitest.core.junit.ApiStackExtension;
import org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static ee.ria.xroad.common.crypto.identifier.SignAlgorithm.SHA256_WITH_ECDSA;
import static ee.ria.xroad.common.crypto.identifier.SignAlgorithm.SHA256_WITH_RSA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.niis.xroad.common.properties.NodeProperties.NodeType.PRIMARY;

/**
 * Shared fixture helpers for every signer scenario class: token/key/cert lookups against the shared
 * {@link SignerIntTestContainerSetup} stack, client id parsing, and the test-CA CSR signing call. Mirrors
 * what the legacy {@code BaseSignerStepDefs}/{@code SignerStepDefs}/{@code TestCaStepDefs} glue did, minus
 * the Cucumber/Spring scaffolding: state that Cucumber scoped per-scenario (key/cert ids captured mid
 * scenario) is now a plain instance field, reset for free by JUnit5's per-method test instance lifecycle.
 */
@ExtendWith(ApiStackExtension.class)
@SuppressWarnings("checkstyle:MagicNumber")
abstract class AbstractSignerIntTest {

    private static final int HTTP_OK = 200;

    private final Map<String, String> tokenFriendlyNameToIdMapping = new HashMap<>();
    private final Map<String, String> tokenLabelToIdMapping = new HashMap<>();

    protected SignerIntTestContainerSetup containerSetup;

    @BeforeEach
    final void injectContainerSetup(SignerIntTestContainerSetup setup) {
        this.containerSetup = setup;
    }

    protected SignerRpcClient client() {
        return client(PRIMARY);
    }

    protected SignerRpcClient client(NodeProperties.NodeType nodeType) {
        return containerSetup.client(nodeType);
    }

    protected SignerSignRpcClient signClient(NodeProperties.NodeType nodeType) {
        return containerSetup.signClient(nodeType);
    }

    /**
     * Rebuilds the friendly-name/label to token-id lookup maps from the current token list. Mirrors the
     * legacy {@code Background: Given tokens are listed} step, which re-ran before every scenario.
     */
    protected void listTokens() {
        tokenFriendlyNameToIdMapping.clear();
        tokenLabelToIdMapping.clear();

        client().getTokens().forEach(token -> {
            if (StringUtils.isNotBlank(token.getLabel())) {
                tokenLabelToIdMapping.put(token.getLabel(), token.getId());
            }
            if (StringUtils.isNotBlank(token.getFriendlyName())) {
                tokenFriendlyNameToIdMapping.put(token.getFriendlyName(), token.getId());
            }
        });
    }

    protected String tokenIdByFriendlyName(String friendlyName) {
        return tokenFriendlyNameToIdMapping.get(friendlyName);
    }

    protected String tokenIdByLabel(String label) {
        return tokenLabelToIdMapping.get(label);
    }

    protected TokenInfo tokenInfoByFriendlyName(String friendlyName) {
        return tokenInfoByFriendlyName(friendlyName, PRIMARY);
    }

    protected TokenInfo tokenInfoByFriendlyName(String friendlyName, NodeProperties.NodeType nodeType) {
        return client(nodeType).getToken(tokenIdByFriendlyName(friendlyName));
    }

    protected KeyInfo findKeyInToken(String friendlyName, String keyName) {
        return findKeyInToken(friendlyName, keyName, PRIMARY);
    }

    protected KeyInfo findKeyInToken(String friendlyName, String keyName, NodeProperties.NodeType nodeType) {
        return tokenInfoByFriendlyName(friendlyName, nodeType).getKeyInfo().stream()
                .filter(keyInfo -> keyName.equals(keyInfo.getFriendlyName()))
                .findFirst()
                .orElseThrow();
    }

    protected static ClientId.Conf clientId(String client) {
        var parts = client.split(":");
        return ClientId.Conf.create(parts[0], parts[1], parts[2]);
    }

    protected static SecurityServerId.Conf securityServerId(String securityServerId) {
        var parts = securityServerId.split(":");
        return SecurityServerId.Conf.create(parts[0], parts[1], parts[2], parts[3]);
    }

    protected static ee.ria.xroad.common.crypto.identifier.SignAlgorithm signAlgorithmFor(KeyInfo key) {
        return switch (ee.ria.xroad.common.crypto.identifier.SignMechanism.valueOf(key.getSignMechanismName()).keyAlgorithm()) {
            case RSA -> SHA256_WITH_RSA;
            case EC -> SHA256_WITH_ECDSA;
        };
    }

    protected static void assertXrdException(String errorCode, String messagePattern, XrdRuntimeException e) {
        assertThat(e.getErrorCode()).isEqualTo(errorCode);
        assertThat(e.getMessage())
                .as("Expected message to match pattern: " + messagePattern)
                .matches(messagePattern);
    }

    protected static void assertAccessDenied(ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isExactlyInstanceOf(XrdRuntimeException.class)
                .hasMessageContaining("signer.access_denied: Write operations are not allowed on secondary node");
    }

    /**
     * Posts a generated CSR to the test CA's {@code /sign} endpoint and returns the resulting certificate
     * as a temp file - a like-for-like RestAssured replacement for the legacy Feign {@code FeignTestCaApi}.
     */
    @SneakyThrows
    protected File signCsr(File csrFile, CsrType type) {
        var response = RestAssuredFactory.given()
                .multiPart("certreq", csrFile)
                .queryParam("type", type.toString())
                .post(containerSetup.testCaBaseUrl() + "/sign");
        response.then().statusCode(HTTP_OK);

        File cert = File.createTempFile("tmp", type.name().toLowerCase() + "_cert" + System.currentTimeMillis());
        Files.write(cert.toPath(), response.getBody().asByteArray());
        return cert;
    }

    protected enum CsrType {
        SIGN, AUTH, AUTO;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }
}
