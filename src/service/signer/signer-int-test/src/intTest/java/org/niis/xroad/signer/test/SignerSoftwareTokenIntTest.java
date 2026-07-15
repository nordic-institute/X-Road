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

import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.niis.xroad.signer.protocol.dto.TokenStatusInfo;
import org.niis.xroad.test.apitest.core.junit.Step;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.common.properties.NodeProperties.NodeType.SECONDARY;
import static org.niis.xroad.signer.test.container.SignerIntTestContainerSetup.SIGNER;

/**
 * 0100 - Signer: SoftToken. Initializes and (de)activates {@code soft-token-000}, the software token
 * every later software-token scenario class assumes is already initialized. Scenarios mutate shared token
 * state and must run in this exact order.
 */
@Order(100)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SignerSoftwareTokenIntTest extends AbstractSignerIntTest {

    private static final String TOKEN_SOFT_000 = "soft-token-000";
    private static final String TOKEN_ID_0 = "0";
    private static final String PIN = "1234";
    private static final String AUTOLOGIN_PIN = "4321";
    private static final int AUTOLOGIN_WAIT_SECONDS = 4;

    @BeforeEach
    void listTokensBeforeEachScenario() {
        listTokens();
    }

    @Test
    @Order(1)
    @DisplayName("Token has its friendly name updated")
    void tokenHasItsFriendlyNameUpdated() {
        Step.when("name \"" + TOKEN_SOFT_000 + "\" is set for token with id \"0\"",
                () -> client().setTokenFriendlyName(TOKEN_ID_0, TOKEN_SOFT_000));
        Step.then("token with id \"0\" name is \"" + TOKEN_SOFT_000 + "\" on primary node",
                () -> assertThat(client().getToken(TOKEN_ID_0).getFriendlyName()).isEqualTo(TOKEN_SOFT_000));
        Step.when("secondary node sync is forced", () -> client(SECONDARY).refreshModules());
        Step.then("token with id \"0\" name is \"" + TOKEN_SOFT_000 + "\" on secondary node",
                () -> assertThat(client(SECONDARY).getToken(TOKEN_ID_0).getFriendlyName()).isEqualTo(TOKEN_SOFT_000));
    }

    @Test
    @Order(2)
    @DisplayName("Token is in initialized")
    void tokenIsInitialized() {
        Step.given("tokens list contains token \"" + TOKEN_SOFT_000 + "\"", () -> assertThat(tokenIdByFriendlyName(TOKEN_SOFT_000))
                .isNotNull());
        Step.and("token \"" + TOKEN_SOFT_000 + "\" status is \"NOT_INITIALIZED\"",
                () -> assertThat(tokenInfoByFriendlyName(TOKEN_SOFT_000).getStatus()).isEqualTo(TokenStatusInfo.NOT_INITIALIZED));
        Step.when("signer is initialized with pin \"" + PIN + "\"", () -> client().initSoftwareToken(PIN.toCharArray()));
        Step.then("token \"" + TOKEN_SOFT_000 + "\" is not active",
                () -> assertThat(tokenInfoByFriendlyName(TOKEN_SOFT_000).isActive()).isFalse());
        Step.and("token \"" + TOKEN_SOFT_000 + "\" status is \"OK\"",
                () -> assertThat(tokenInfoByFriendlyName(TOKEN_SOFT_000).getStatus()).isEqualTo(TokenStatusInfo.OK));
    }

    @Test
    @Order(3)
    @DisplayName("Token must be manually activated when the autologin configuration does not match")
    void tokenMustBeManuallyActivatedWhenAutologinConfigDoesNotMatch() {
        Step.when("signer service is restarted", () -> containerSetup.restartContainer(SIGNER));
        Step.and("waiting " + AUTOLOGIN_WAIT_SECONDS + " seconds for auto-login to take effect",
                () -> sleepSeconds(AUTOLOGIN_WAIT_SECONDS));
        listTokens();
        Step.given("token \"" + TOKEN_SOFT_000 + "\" is not active",
                () -> assertThat(tokenInfoByFriendlyName(TOKEN_SOFT_000).isActive()).isFalse());
        Step.when("token \"" + TOKEN_SOFT_000 + "\" is logged in with pin \"" + PIN + "\"",
                () -> client().activateToken(tokenIdByFriendlyName(TOKEN_SOFT_000), PIN.toCharArray()));
        Step.then("token \"" + TOKEN_SOFT_000 + "\" is active",
                () -> assertThat(tokenInfoByFriendlyName(TOKEN_SOFT_000).isActive()).isTrue());
    }

    @Test
    @Order(4)
    @DisplayName("Token is deactivated")
    void tokenIsDeactivated() {
        Step.when("token \"" + TOKEN_SOFT_000 + "\" is logged out",
                () -> client().deactivateToken(tokenIdByFriendlyName(TOKEN_SOFT_000)));
        Step.then("token \"" + TOKEN_SOFT_000 + "\" is not active",
                () -> assertThat(tokenInfoByFriendlyName(TOKEN_SOFT_000).isActive()).isFalse());
    }

    @Test
    @Order(5)
    @DisplayName("Autologin works properly when token pin is updated to match the autologin configuration")
    void autologinWorksWhenPinUpdatedToMatchAutologinConfig() {
        Step.given("token \"" + TOKEN_SOFT_000 + "\" is not active",
                () -> assertThat(tokenInfoByFriendlyName(TOKEN_SOFT_000).isActive()).isFalse());
        Step.and("token \"" + TOKEN_SOFT_000 + "\" is logged in with pin \"" + PIN + "\"",
                () -> client().activateToken(tokenIdByFriendlyName(TOKEN_SOFT_000), PIN.toCharArray()));
        Step.when("token \"" + TOKEN_SOFT_000 + "\" pin is updated from \"" + PIN + "\" to \"" + AUTOLOGIN_PIN + "\"",
                () -> client().updateTokenPin(tokenIdByFriendlyName(TOKEN_SOFT_000), PIN.toCharArray(), AUTOLOGIN_PIN.toCharArray()));
        Step.and("signer service is restarted", () -> containerSetup.restartContainer(SIGNER));
        Step.and("waiting " + AUTOLOGIN_WAIT_SECONDS + " seconds for auto-login to take effect",
                () -> sleepSeconds(AUTOLOGIN_WAIT_SECONDS));
        listTokens();
        Step.then("token \"" + TOKEN_SOFT_000 + "\" is active",
                () -> assertThat(tokenInfoByFriendlyName(TOKEN_SOFT_000).isActive()).isTrue());
    }

    @SneakyThrows
    private void sleepSeconds(int seconds) {
        TimeUnit.SECONDS.sleep(seconds);
    }
}
