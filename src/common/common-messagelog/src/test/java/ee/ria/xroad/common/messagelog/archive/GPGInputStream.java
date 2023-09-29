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
package ee.ria.xroad.common.messagelog.archive;

import lombok.extern.slf4j.Slf4j;

import java.io.FilterInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
class GPGInputStream extends FilterInputStream {

    private final Process gpg;
    private final Path statusTmp;
    private List<String> statusText = Collections.emptyList();

    private static final String[] DEFAULT_ARGS = {
            "--batch",
            "--no-tty",
            "--yes",
            "--decrypt",
    };

    GPGInputStream(Path gpgHome, Path input) throws IOException {
        super(null);
        statusTmp = Files.createTempFile("gpgstatus", ".tmp");
        final ProcessBuilder builder = new ProcessBuilder("/usr/bin/gpg");

        builder.command().add("--homedir");
        builder.command().add(gpgHome.toString());

        builder.command().addAll(Arrays.asList(DEFAULT_ARGS));

        builder.command().add("--status-file");
        builder.command().add(statusTmp.toString());

        builder.command().add("--output");
        builder.command().add("-");

        builder.command().add(input.toString());

        //status output as well as actual output are directed to a file
        //discard any standard output/error so that the process does not block in any case.
        builder.redirectError(ProcessBuilder.Redirect.to(Paths.get("/dev/null").toFile()));
        gpg = builder.start();

        in = gpg.getInputStream();
        gpg.getOutputStream().close();
    }

    @Override
    public void close() throws IOException {
        try {
            if (in != null) {
                super.close();
            }
            gpg.waitFor(10, TimeUnit.SECONDS);
            statusText = Files.readAllLines(statusTmp, StandardCharsets.UTF_8);
            if (gpg.isAlive()) {
                log.debug("Decryption failed: {}", statusText);
                throw new IOException("Decryption failed, subprocess not stopping");
            }
            if (gpg.exitValue() != 0) {
                log.debug("Decryption failed: {}", statusText);
                throw new IOException("Decryption failed, subprocess exit code: " + gpg.exitValue());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (gpg.isAlive()) {
                //interrupted while waiting for gpg process to exit, signal failure
                throw new IOException(e);
            }
        } finally {
            try {
                Files.deleteIfExists(statusTmp);
            } catch (IOException e) {
                //ignore
            }
            gpg.destroyForcibly();
        }
    }

    int getExitCode() {
        return gpg.exitValue();
    }

    List<String> getStatus() {
        return statusText;
    }
}
