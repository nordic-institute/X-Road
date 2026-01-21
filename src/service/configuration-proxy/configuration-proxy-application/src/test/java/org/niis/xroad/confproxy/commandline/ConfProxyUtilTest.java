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
package org.niis.xroad.confproxy.commandline;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConfProxyUtilTest {

    private static final String RUNNER = "org.niis.xroad.confproxy.commandline.ConfProxyUtilTest$CliTestRunner";

    private record ProcResult(int exitCode, String output) { }

    private static ProcResult runCliInForkedJvm(String... args) throws Exception {
        String javaBin = System.getProperty("java.home") + "/bin/java";
        String classPath = System.getProperty("java.class.path");

        List<String> cmd = new ArrayList<>();
        cmd.add(javaBin);
        cmd.add("-cp");
        cmd.add(classPath);
        cmd.add(RUNNER);
        cmd.addAll(Arrays.asList(args));

        Process p = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start();

        String output;
        try (InputStream is = p.getInputStream()) {
            output = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        int exit = p.waitFor();
        return new ProcResult(exit, output);
    }

    @Test
    void runUtilWithArgsConfProxyUtilAddSigningKeyIsRunnableFromCli() throws Exception {
        ProcResult r = runCliInForkedJvm("org.niis.xroad.confproxy.commandline.ConfProxyUtilAddSigningKey");

        assertThat(r.output)
                .containsIgnoringCase("confproxy-add-signing-key")
                .containsIgnoringCase("key-id")
                .containsIgnoringCase("token-id");
    }

    @Test
    void runUtilWithArgsConfProxyUtilCreateInstanceIsRunnableFromCli() throws Exception {
        ProcResult r = runCliInForkedJvm("org.niis.xroad.confproxy.commandline.ConfProxyUtilCreateInstance");

        assertThat(r.output)
                .containsIgnoringCase("confproxy-create-instance")
                .containsIgnoringCase("usage");
    }

    @Test
    void runUtilWithArgsConfProxyUtilDelSigningKeyIsRunnableFromCli() throws Exception {
        ProcResult r = runCliInForkedJvm("org.niis.xroad.confproxy.commandline.ConfProxyUtilDelSigningKey");

        assertThat(r.output)
                .containsIgnoringCase("confproxy-del-signing-key")
                .containsIgnoringCase("key-id");
    }

    @Test
    void runUtilWithArgsConfProxyUtilGenerateAnchorIsRunnableFromCli() throws Exception {
        ProcResult r = runCliInForkedJvm("org.niis.xroad.confproxy.commandline.ConfProxyUtilGenerateAnchor");

        assertThat(r.output)
                .containsIgnoringCase("confproxy-generate-anchor")
                .containsIgnoringCase("filename");
    }

    @Test
    void runUtilWithArgsConfProxyUtilViewConfIsRunnableFromCli() throws Exception {
        ProcResult r = runCliInForkedJvm("org.niis.xroad.confproxy.commandline.ConfProxyUtilViewConf");

        assertThat(r.output)
                .containsIgnoringCase("confproxy-view-conf")
                .containsIgnoringCase("usage");
    }

    /**
     * Runs the CLI inside a forked JVM so System.exit() (if any) can't kill the test JVM.
     * Also initializes the ConfProxyUtilMain static fields that are null in the forked process.
     */
    public static final class CliTestRunner {
        private CliTestRunner() {
        }

        private static void setStaticField(String fieldName, Object value) throws Exception {
            var f = ConfProxyUtilMain.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(null, value);
        }

        public static void main(String[] args) throws Exception {
            setStaticField("cmdLineParser", new org.apache.commons.cli.DefaultParser());
            setStaticField("signerRpcClient",
                    org.mockito.Mockito.mock(org.niis.xroad.signer.client.SignerRpcClient.class));

            ConfProxyUtilMain.runUtilWithArgs(args);
        }
    }
}
