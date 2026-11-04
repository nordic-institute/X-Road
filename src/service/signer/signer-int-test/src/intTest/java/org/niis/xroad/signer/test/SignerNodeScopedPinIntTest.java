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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.niis.xroad.test.apitest.core.junit.Step;
import org.testcontainers.containers.Container.ExecResult;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.niis.xroad.signer.test.container.SignerIntTestContainerSetup.SIGNER;

/**
 * 0450 - Signer: node-scoped PIN storage. The primary signer runs as node {@code node_a} and the secondary as
 * node {@code 0}, both storing the PIN of token {@code 0} in the shared OpenBao. Reads OpenBao directly to
 * prove that each node keeps its own PIN, that node {@code 0} and token {@code 0} do not collide, that restart
 * auto-login reads the node path without changing it, and that deleting one node's PIN leaves the other's.
 */
@Order(450)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SignerNodeScopedPinIntTest extends AbstractSignerIntTest {

    private static final String OPENBAO = "openbao";
    private static final String MOUNT = "-mount=xrd-secret";
    private static final String PIN_BASE_PATH = "signer/token-pins";
    private static final String PRIMARY_NODE_PIN_DIR = "signer/token-pins/node_a";
    private static final String SECONDARY_NODE_PIN_DIR = "signer/token-pins/0";
    private static final String PRIMARY_NODE_PIN_PATH = "signer/token-pins/node_a/0";
    private static final String SECONDARY_NODE_PIN_PATH = "signer/token-pins/0/0";
    private static final String FLAT_PIN_PATH = "signer/token-pins/0";
    private static final String TOKEN_ID_0 = "0";
    private static final String AUTOLOGIN_PIN = "4321";
    private static final String TEMPORARY_PIN = "5678";
    private static final Duration AUTOLOGIN_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration AUTOLOGIN_POLL_INTERVAL = Duration.ofMillis(500);

    @Test
    @Order(1)
    @DisplayName("Each node stores the PIN of token 0 under its own node path")
    void eachNodeStoresPinUnderItsOwnPath() {
        Step.then("primary node path holds the autologin pin",
                () -> assertThat(readPin(PRIMARY_NODE_PIN_PATH)).isEqualTo(AUTOLOGIN_PIN));
        Step.and("secondary node path holds the autologin pin",
                () -> assertThat(readPin(SECONDARY_NODE_PIN_PATH)).isEqualTo(AUTOLOGIN_PIN));
        Step.and("the flat token pin path is not written", () -> assertThat(pinExists(FLAT_PIN_PATH)).isFalse());
    }

    @Test
    @Order(2)
    @DisplayName("Auto-login after restart reads the node path and leaves OpenBao unchanged")
    void autologinAfterRestartReadsNodePath() {
        var pinStoreBeforeRestart = pinStoreSnapshot();

        Step.given("token '0' is logged out", () -> client().deactivateToken(TOKEN_ID_0));
        Step.when("signer service is restarted", () -> containerSetup.restartContainer(SIGNER));
        Step.then("token '0' becomes active through auto-login", this::awaitTokenActive);
        Step.and("token pin keys and values in OpenBao are unchanged",
                () -> assertThat(pinStoreSnapshot()).isEqualTo(pinStoreBeforeRestart));
        Step.and("the flat token pin path is not written", () -> assertThat(pinExists(FLAT_PIN_PATH)).isFalse());
    }

    @Test
    @Order(3)
    @DisplayName("Deleting the PIN of one node leaves the PIN of the other node")
    void deletingOneNodePinLeavesTheOther() {
        Step.when("token '0' pin is updated on the primary node, clearing its stored pin",
                () -> client().updateTokenPin(TOKEN_ID_0, AUTOLOGIN_PIN.toCharArray(), TEMPORARY_PIN.toCharArray()));
        Step.then("primary node path no longer holds a pin", () -> assertThat(pinExists(PRIMARY_NODE_PIN_PATH)).isFalse());
        Step.and("secondary node path still holds its pin",
                () -> assertThat(readPin(SECONDARY_NODE_PIN_PATH)).isEqualTo(AUTOLOGIN_PIN));

        Step.when("the original pin is restored and the signer restarted", this::restoreAutologinPin);
        Step.then("primary node path holds the autologin pin again",
                () -> assertThat(readPin(PRIMARY_NODE_PIN_PATH)).isEqualTo(AUTOLOGIN_PIN));
        Step.and("token '0' is active", this::awaitTokenActive);
    }

    private void restoreAutologinPin() {
        client().activateToken(TOKEN_ID_0, TEMPORARY_PIN.toCharArray());
        client().updateTokenPin(TOKEN_ID_0, TEMPORARY_PIN.toCharArray(), AUTOLOGIN_PIN.toCharArray());
        containerSetup.restartContainer(SIGNER);
        awaitTokenActive();
    }

    private void awaitTokenActive() {
        await().atMost(AUTOLOGIN_TIMEOUT)
                .pollInterval(AUTOLOGIN_POLL_INTERVAL)
                .ignoreExceptions()
                .untilAsserted(() -> assertThat(client().getToken(TOKEN_ID_0).isActive()).isTrue());
    }

    private ExecResult bao(String... kvArguments) {
        var command = new String[kvArguments.length + 2];
        command[0] = "bao";
        command[1] = "kv";
        System.arraycopy(kvArguments, 0, command, 2, kvArguments.length);
        return containerSetup.execInContainer(OPENBAO, command);
    }

    private boolean pinExists(String path) {
        return bao("get", MOUNT, "-field=pin", path).getExitCode() == 0;
    }

    private String readPin(String path) {
        var result = bao("get", MOUNT, "-field=pin", path);
        assertThat(result.getExitCode()).as("bao kv get %s: %s", path, result.getStderr()).isZero();
        return result.getStdout().trim();
    }

    private String pinStoreSnapshot() {
        return String.join("\n",
                listKeys(PIN_BASE_PATH),
                listKeys(PRIMARY_NODE_PIN_DIR),
                listKeys(SECONDARY_NODE_PIN_DIR),
                readPin(PRIMARY_NODE_PIN_PATH),
                readPin(SECONDARY_NODE_PIN_PATH));
    }

    private String listKeys(String path) {
        var result = bao("list", MOUNT, path);
        assertThat(result.getExitCode()).as("bao kv list %s: %s", path, result.getStderr()).isZero();
        return result.getStdout().trim();
    }
}
