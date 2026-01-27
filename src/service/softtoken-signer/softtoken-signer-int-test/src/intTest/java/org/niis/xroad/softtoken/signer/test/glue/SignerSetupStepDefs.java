/*
 * The MIT License
 *
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

package org.niis.xroad.softtoken.signer.test.glue;

import io.cucumber.java.After;
import io.cucumber.java.en.Step;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.signer.api.dto.TokenInfo;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

@Slf4j
@SuppressWarnings("checkstyle:MagicNumber")
public class SignerSetupStepDefs extends BaseSoftTokenSignerStepDefs {

    @Step("signer is initialized with PIN {string}")
    public void signerIsInitializedWithPin(String pin) {
        log.info("Initializing signer with PIN");
        getSignerClient().initSoftwareToken(pin.toCharArray());
        testReportService.attachText("Signer initialization", "Completed successfully");
    }

    @Step("token {string} is logged in with PIN {string}")
    public void tokenIsLoggedInWithPin(String tokenFriendlyName, String pin) {
        log.info("Logging in token: {}", tokenFriendlyName);
        TokenInfo token = getTokenByFriendlyName(tokenFriendlyName);
        getSignerClient().activateToken(token.getId(), pin.toCharArray());

        getTokenIdMapping().put(tokenFriendlyName, token.getId());

        testReportService.attachText("Token login", tokenFriendlyName + " logged in successfully");
    }

    @Step("signer is initialized and running")
    public void signerIsInitializedAndRunning() {
        signerIsInitializedWithPin("1234");
        tokenIsLoggedInWithPin("soft-token-000", "1234");
    }

    @Step("signer is initialized and keys are synchronized")
    public void signerIsInitializedAndKeysSynchronized() {
        signerIsInitializedAndRunning();
        waitForSynchronization();
    }

    @Step("token {string} is active")
    public void tokenIsActive(String tokenFriendlyName) {
        TokenInfo token = getTokenByFriendlyName(tokenFriendlyName);
        assertThat(token.isActive())
                .as("Token %s should be active", tokenFriendlyName)
                .isTrue();
        testReportService.attachText("Token status", tokenFriendlyName + " is active");
    }

    /**
     * Cleanup after each scenario.
     * Deletes all created keys to ensure test isolation.
     */
    @After
    public void cleanup() {
        log.info("Cleaning up test keys");
        var createdKeys = getCreatedKeys();

        createdKeys.values().forEach(key -> {
            try {
                log.debug("Deleting key: {}", key.getFriendlyName());
                getSignerClient().deleteKey(key.getId(), true);
            } catch (Exception e) {
                log.warn("Failed to delete key during cleanup: {}", key.getFriendlyName(), e);
            }
        });

        createdKeys.clear();
        log.info("Cleanup completed");
    }
}
