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

package org.niis.xroad.common.properties.dbsource;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs the installer's {@code check_node_name} over the shared node name test table and expects the verdict
 * {@link NodeNameTest} asserts for {@link NodeName}. Skipped when bash or the script is not available on the test
 * host.
 */
class NodeNameShellParityTest {

    static final String SCRIPT_PROPERTY = "xroad.test.node-name-script";

    private static final String CHECK_COMMAND = "source \"$1\"; check_node_name \"$2\" \"the test table\"";

    @ParameterizedTest
    @MethodSource("org.niis.xroad.common.properties.dbsource.NodeNameCases#all")
    void shellVerdictMatchesNodeName(NodeNameCases.Case testCase) throws IOException, InterruptedException {
        var script = Path.of(System.getProperty(SCRIPT_PROPERTY, ""));
        assumeTrue(Files.isRegularFile(script) && bashAvailable(), "bash or node name script not available");

        assertThat(shellAccepts(script, testCase.value()))
                .as("shell verdict for %s", testCase)
                .isEqualTo(testCase.accepted());
    }

    private static boolean shellAccepts(Path script, String value) throws IOException, InterruptedException {
        var process = new ProcessBuilder("bash", "-c", CHECK_COMMAND, "bash", script.toString(), value)
                .redirectErrorStream(true)
                .start();
        process.getOutputStream().close();
        process.getInputStream().readAllBytes();
        assertThat(process.waitFor(10, TimeUnit.SECONDS)).as("bash finished").isTrue();
        return process.exitValue() == 0;
    }

    private static boolean bashAvailable() throws InterruptedException {
        try {
            var process = new ProcessBuilder("bash", "-c", "exit 0").redirectErrorStream(true).start();
            return process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException _) {
            return false;
        }
    }
}
