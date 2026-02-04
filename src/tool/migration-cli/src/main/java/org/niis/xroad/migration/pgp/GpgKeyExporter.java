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
package org.niis.xroad.migration.pgp;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Exports PGP keys from GPG home directory using gpg command-line tool.
 */
@Slf4j
public class GpgKeyExporter {

    /**
     * Exports secret and public keys from GPG home directory.
     *
     * @param gpgHome GPG home directory
     * @param secretKeyOutput Output file for secret keys
     * @param publicKeysOutput Output file for public keys
     * @throws IOException if export fails
     */
    public void exportKeys(Path gpgHome, Path secretKeyOutput, Path publicKeysOutput) throws IOException {
        log.info("Exporting keys from GPG home: {}", gpgHome);

        exportSecretKeys(gpgHome, secretKeyOutput);
        exportPublicKeys(gpgHome, publicKeysOutput);

        log.info("Keys exported successfully");
    }

    private void exportSecretKeys(Path gpgHome, Path outputFile) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
                "gpg",
                "--homedir", gpgHome.toString(),
                "--export-secret-keys",
                "--armor",
                "--output", outputFile.toString()
        );

        executeGpgCommand(pb, "export-secret-keys");
        log.info("Exported secret keys to: {}", outputFile);
    }

    private void exportPublicKeys(Path gpgHome, Path outputFile) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
                "gpg",
                "--homedir", gpgHome.toString(),
                "--export",
                "--armor",
                "--output", outputFile.toString()
        );

        executeGpgCommand(pb, "export");
        log.info("Exported public keys to: {}", outputFile);
    }

    private void executeGpgCommand(ProcessBuilder pb, String operation) throws IOException {
        try {
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                String errorOutput = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                throw new IOException("GPG %s failed with exit code %d: %s"
                        .formatted(operation, exitCode, errorOutput));
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("GPG " + operation + " interrupted", e);
        }
    }
}


