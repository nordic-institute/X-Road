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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.signer.protocol.dto.TokenStatusInfo;
import org.niis.xroad.test.apitest.core.junit.Step;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.common.properties.NodeProperties.NodeType.SECONDARY;

/**
 * 0200 - Signer: HardwareToken. Uses SoftHSM (baked into the signer container's PKCS#11 module config) to
 * emulate a hardware token; initializes and (de)activates {@code xrd-softhsm-0}, which the later
 * hardware-token key-operations classes assume is already initialized.
 */
@Order(200)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SignerHardwareTokenIntTest extends AbstractSignerIntTest {

    private static final String TOKEN_LABEL = "x-road-softhsm2";
    private static final String TOKEN_HSM_0 = "xrd-softhsm-0";
    private static final String PIN = "1234";

    @BeforeEach
    void listTokensBeforeEachScenario() {
        listTokens();
    }

    @Test
    @Order(1)
    @DisplayName("HSM is operational")
    void hsmIsOperational() {
        Step.given("HSM is operational", () -> assertThat(client().isHSMOperational()).isTrue());
    }

    @Test
    @Order(2)
    @DisplayName("Token has its friendly name updated")
    void tokenHasItsFriendlyNameUpdated() {
        Step.when("friendly name \"" + TOKEN_HSM_0 + "\" is set for token with label \"" + TOKEN_LABEL + "\"",
                () -> client().setTokenFriendlyName(tokenIdByLabel(TOKEN_LABEL), TOKEN_HSM_0));
        Step.and("secondary node sync is forced", () -> client(SECONDARY).refreshModules());
        Step.then("token with label \"" + TOKEN_LABEL + "\" name is \"" + TOKEN_HSM_0 + "\"",
                () -> assertThat(client().getToken(tokenIdByLabel(TOKEN_LABEL)).getFriendlyName()).isEqualTo(TOKEN_HSM_0));
        Step.and("token with label \"" + TOKEN_LABEL + "\" name is \"" + TOKEN_HSM_0 + "\" on secondary node",
                () -> assertThat(client(SECONDARY).getToken(tokenIdByLabel(TOKEN_LABEL)).getFriendlyName()).isEqualTo(TOKEN_HSM_0));
    }

    @Test
    @Order(3)
    @DisplayName("Token is in initialized")
    void tokenIsInitialized() {
        Step.given("tokens list contains token \"" + TOKEN_HSM_0 + "\"", () -> assertThat(tokenIdByFriendlyName(TOKEN_HSM_0)).isNotNull());
        Step.and("token \"" + TOKEN_HSM_0 + "\" status is \"OK\"",
                () -> assertThat(tokenInfoByFriendlyName(TOKEN_HSM_0).getStatus()).isEqualTo(TokenStatusInfo.OK));
    }

    @Test
    @Order(4)
    @DisplayName("Token is activated")
    void tokenIsActivated() {
        Step.given("token \"" + TOKEN_HSM_0 + "\" is not active",
                () -> assertThat(tokenInfoByFriendlyName(TOKEN_HSM_0).isActive()).isFalse());
        Step.when("token \"" + TOKEN_HSM_0 + "\" is logged in with pin \"" + PIN + "\"",
                () -> client().activateToken(tokenIdByFriendlyName(TOKEN_HSM_0), PIN.toCharArray()));
        Step.then("token \"" + TOKEN_HSM_0 + "\" is active",
                () -> assertThat(tokenInfoByFriendlyName(TOKEN_HSM_0).isActive()).isTrue());
    }

    @Test
    @Order(5)
    @DisplayName("Token is deactivated")
    void tokenIsDeactivated() {
        Step.when("token \"" + TOKEN_HSM_0 + "\" is logged out",
                () -> client().deactivateToken(tokenIdByFriendlyName(TOKEN_HSM_0)));
        Step.then("token \"" + TOKEN_HSM_0 + "\" is not active",
                () -> assertThat(tokenInfoByFriendlyName(TOKEN_HSM_0).isActive()).isFalse());
    }

    @Test
    @Order(6)
    @DisplayName("Token pin update is not supported for hardware token")
    void tokenPinUpdateIsNotSupportedForHardwareToken() {
        Step.given("token \"" + TOKEN_HSM_0 + "\" is not active",
                () -> assertThat(tokenInfoByFriendlyName(TOKEN_HSM_0).isActive()).isFalse());
        Step.and("token \"" + TOKEN_HSM_0 + "\" is logged in with pin \"" + PIN + "\"",
                () -> client().activateToken(tokenIdByFriendlyName(TOKEN_HSM_0), PIN.toCharArray()));
        Step.when("token \"" + TOKEN_HSM_0 + "\" pin is update from \"" + PIN + "\" to \"4321\" fails with an error", () -> {
            try {
                client().updateTokenPin(tokenIdByFriendlyName(TOKEN_HSM_0), PIN.toCharArray(), "4321".toCharArray());
            } catch (XrdRuntimeException e) {
                assertXrdException("signer.internal_error",
                        "\\[.*?\\] signer\\.internal_error: Software token not found", e);
            }
        });
        Step.then("token \"" + TOKEN_HSM_0 + "\" is active", () -> assertThat(tokenInfoByFriendlyName(TOKEN_HSM_0).isActive()).isTrue());
    }
}
