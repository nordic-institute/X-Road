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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs {@code yaml_helper.py get} on local.yaml files and expects the value the way the Java configuration loader
 * reads the same file, scalars as written and merge keys expanded. Skipped when no python with PyYAML is available
 * on the test host.
 */
class YamlHelperGetTest {

    static final String SCRIPT_PROPERTY = "xroad.test.yaml-helper-script";

    private static final String NODE_NAME_KEY = "xroad.admin-service.ha-node-name";

    private static final List<String> PYTHON_CANDIDATES =
            List.of("python3", "python3.15", "python3.14", "python3.13", "python3.12", "python3.11", "python3.10");

    private static Path script;
    private static String python;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void findPythonWithYaml() {
        script = Path.of(System.getProperty(SCRIPT_PROPERTY, ""));
        assumeTrue(Files.isRegularFile(script), "yaml_helper.py not available");
        python = PYTHON_CANDIDATES.stream().filter(YamlHelperGetTest::hasYaml).findFirst().orElse(null);
        assumeTrue(python != null, "no python with PyYAML available");
    }

    @Test
    void getReadsValueInheritedThroughMergeKey() throws IOException, InterruptedException {
        var file = writeYaml("""
                defaults: &d
                  ha-node-name: n1
                xroad:
                  admin-service:
                    <<: *d
                """);

        assertThat(get(file)).isEqualTo("n1");
    }

    @Test
    void getReadsOwnValueOverMergedOne() throws IOException, InterruptedException {
        var file = writeYaml("""
                defaults: &d
                  ha-node-name: n1
                xroad:
                  admin-service:
                    <<: *d
                    ha-node-name: n2
                """);

        assertThat(get(file)).isEqualTo("n2");
    }

    @Test
    void getReturnsNumberLookingValueAsWritten() throws IOException, InterruptedException {
        var file = writeYaml("""
                xroad:
                  admin-service:
                    ha-node-name: 0123
                """);

        assertThat(get(file)).isEqualTo("0123");
    }

    @Test
    void getReturnsBooleanLookingValueAsWritten() throws IOException, InterruptedException {
        var file = writeYaml("""
                xroad:
                  admin-service:
                    ha-node-name: on
                """);

        assertThat(get(file)).isEqualTo("on");
    }

    private Path writeYaml(String content) throws IOException {
        return Files.writeString(tempDir.resolve("local.yaml"), content, StandardCharsets.UTF_8);
    }

    private static String get(Path file) throws IOException, InterruptedException {
        var process = new ProcessBuilder(python, script.toString(), "get", file.toString(), NODE_NAME_KEY)
                .redirectErrorStream(true)
                .start();
        process.getOutputStream().close();
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor(10, TimeUnit.SECONDS)).as("python finished").isTrue();
        assertThat(process.exitValue()).as("yaml_helper.py exit code, output: %s", output).isZero();
        return output.strip();
    }

    private static boolean hasYaml(String candidate) {
        try {
            var process = new ProcessBuilder(candidate, "-c", "import yaml").redirectErrorStream(true).start();
            process.getOutputStream().close();
            process.getInputStream().readAllBytes();
            return process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException _) {
            return false;
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
