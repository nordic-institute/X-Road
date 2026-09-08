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
package org.niis.xroad.confproxy.cli;

import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.core.annotation.ArchUnitSuppressed;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusMainTest
@ArchUnitSuppressed(value = "NoQuarkusTestAnnotations", reason = "CLI tests need QuarkusTest annotations to run")
@TestProfile(ConfProxyCLITestProfile.class)
class ConfProxyUtilTest {

    @Test
    @Launch({})
    void noArgsShowsAvailableActions(LaunchResult result) {
        assertThat(result.getOutput())
                .contains("Action name is required")
                .contains("Available actions")
                .contains("add-signing-key")
                .contains("create-instance")
                .contains("del-signing-key")
                .contains("generate-anchor")
                .contains("view-conf")
                .contains("list-api-keys")
                .contains("generate-api-key")
                .contains("revoke-api-key")
                .contains("activate-signing-key")
                .contains("download-conf");
    }

    @Test
    @Launch("nonexistent-action")
    void unknownActionShowsAvailableActions(LaunchResult result) {
        assertThat(result.getOutput())
                .contains("Available actions");
    }

    @Test
    @Launch("add-signing-key")
    void addSigningKeyShowsHelp(LaunchResult result) {
        assertThat(result.getOutput())
                .contains("add-signing-key")
                .contains("--key-id")
                .contains("--token-id")
                .contains("--proxy-instance");
    }

    @Test
    @Launch("create-instance")
    void createInstanceShowsHelp(LaunchResult result) {
        assertThat(result.getOutput())
                .contains("create-instance")
                .contains("--proxy-instance");
    }

    @Test
    @Launch("del-signing-key")
    void delSigningKeyShowsHelp(LaunchResult result) {
        assertThat(result.getOutput())
                .contains("del-signing-key")
                .contains("--key-id")
                .contains("--proxy-instance");
    }

    @Test
    @Launch("generate-anchor")
    void generateAnchorShowsHelp(LaunchResult result) {
        assertThat(result.getOutput())
                .contains("generate-anchor")
                .contains("--filename")
                .contains("--proxy-instance");
    }

    @Test
    @Launch("view-conf")
    void viewConfShowsHelp(LaunchResult result) {
        assertThat(result.getOutput())
                .contains("view-conf")
                .contains("--proxy-instance");
    }

    @Test
    @Launch("activate-signing-key")
    void activateSigningKeyShowsHelp(LaunchResult result) {
        assertThat(result.getOutput())
                .contains("activate-signing-key")
                .contains("--key-id")
                .contains("--proxy-instance");
    }

    @Test
    @Launch("generate-api-key")
    void generateApiKeyShowsHelp(LaunchResult result) {
        assertThat(result.getOutput())
                .contains("generate-api-key")
                .contains("--roles");
    }

    @Test
    @Launch("revoke-api-key")
    void revokeApiKeyShowsHelp(LaunchResult result) {
        assertThat(result.getOutput())
                .contains("revoke-api-key")
                .contains("--id");
    }

    @Test
    @Launch(value = "list-api-keys", exitCode = 1)
    void listApiKeysFailsWithoutDatabase(LaunchResult result) {
        assertThat(result.getErrorOutput())
                .contains("database_error");
    }
}
