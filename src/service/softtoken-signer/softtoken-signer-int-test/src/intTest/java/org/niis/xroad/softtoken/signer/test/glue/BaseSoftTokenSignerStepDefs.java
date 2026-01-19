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

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.client.impl.SignerSignRpcClient;
import org.niis.xroad.softtoken.signer.test.SoftTokenSignerClientHolder;
import org.niis.xroad.softtoken.signer.test.SoftTokenSignerIntTestContainerSetup;
import org.niis.xroad.test.framework.core.context.CucumberScenarioProvider;
import org.niis.xroad.test.framework.core.context.ScenarioContext;
import org.niis.xroad.test.framework.core.report.TestReportService;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class BaseSoftTokenSignerStepDefs {
    private static final String CREATED_KEYS_MAP = "createdKeysMap";
    private static final String TOKEN_ID_MAPPING = "tokenIdMapping";
    private static final int KEYS_SYNC_BUFFER_SECONDS = 1;
    private static final int KEYS_SYNC_RATE_SECONDS = 3;

    @Autowired
    protected CucumberScenarioProvider scenarioProvider;

    @Autowired
    protected ScenarioContext scenarioContext;

    @Autowired
    protected TestReportService testReportService;

    @Autowired
    protected SoftTokenSignerClientHolder softtokenClientHolder;

    @Autowired
    protected SoftTokenSignerIntTestContainerSetup containerSetup;

    protected SignerRpcClient getSignerClient() {
        return softtokenClientHolder.getSignerClient();
    }

    protected SignerSignRpcClient getSoftTokenSignerSignClient() {
        return softtokenClientHolder.getSoftTokenSignerSignClient();
    }

    protected Map<String, KeyInfo> getCreatedKeys() {
        Map<String, KeyInfo> map = scenarioContext.getStepData(CREATED_KEYS_MAP);
        if (map == null) {
            map = new HashMap<>();
            scenarioContext.putStepData(CREATED_KEYS_MAP, map);
        }
        return map;
    }

    protected Map<String, String> getTokenIdMapping() {
        Map<String, String> map = scenarioContext.getStepData(TOKEN_ID_MAPPING);
        if (map == null) {
            map = new HashMap<>();
            scenarioContext.putStepData(TOKEN_ID_MAPPING, map);
        }
        return map;
    }

    protected TokenInfo getTokenByFriendlyName(String friendlyName) {
        var tokens = getSignerClient().getTokens();

        // First try by friendly name
        Optional<TokenInfo> token = tokens.stream()
                .filter(t -> friendlyName.equals(t.getFriendlyName()))
                .findFirst();

        // Fall back to ID "0" for default software token
        if (token.isEmpty() && "soft-token-000".equals(friendlyName)) {
            log.debug("Token not found by friendly name '{}', trying by ID '0'", friendlyName);
            token = tokens.stream()
                    .filter(t -> "0".equals(t.getId()))
                    .findFirst();
        }

        if (token.isPresent()) {
            testReportService.attachJson("TokenInfo [" + friendlyName + "]", token.get());
            return token.get();
        }

        throw new AssertionError("Token not found: " + friendlyName);
    }

    protected KeyInfo findKeyInToken(String tokenFriendlyName, String keyLabel) {
        var tokenInfo = getTokenByFriendlyName(tokenFriendlyName);
        Optional<KeyInfo> key = tokenInfo.getKeyInfo().stream()
                .filter(k -> keyLabel.equals(k.getFriendlyName()))
                .findFirst();

        if (key.isPresent()) {
            testReportService.attachJson("KeyInfo [" + keyLabel + "]", key.get());
            return key.get();
        }

        throw new AssertionError("Key not found in token " + tokenFriendlyName + ": " + keyLabel);
    }

    protected void waitForSynchronization() {
        int waitSeconds = KEYS_SYNC_RATE_SECONDS + KEYS_SYNC_BUFFER_SECONDS;

        log.info("Waiting {} seconds for key synchronization (interval: {}s, buffer: {}s)",
                waitSeconds, KEYS_SYNC_RATE_SECONDS, KEYS_SYNC_BUFFER_SECONDS);

        try {
            Thread.sleep(Duration.ofSeconds(waitSeconds).toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Synchronization wait interrupted", e);
        }
    }

    protected void putStepData(String key, Object value) {
        scenarioContext.putStepData(key, value);
    }

    protected <T> T getStepData(String key) {
        return scenarioContext.getStepData(key);
    }
}
