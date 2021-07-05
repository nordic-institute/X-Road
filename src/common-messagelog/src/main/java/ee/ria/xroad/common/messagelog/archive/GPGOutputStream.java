/**
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

import ee.ria.xroad.common.SystemProperties;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Outputstream that pipes output to an external GnuPG process for signing and encryption
 */
@Slf4j
class GPGOutputStream extends FilterOutputStream {

    private final Process gpg;
    private final Path statusTmp;

    private static final String[] DEFAULT_ARGS = {
            "--batch",
            "--no-tty",
            "--yes",
            "--encrypt",
            "--sign",
            //sane cipher setting overrides (disables negotiation)
            "--compress-algo", "none",
            "--cipher-algo", "AES",
            "--digest-algo", "SHA256"
    };

    /**
     * Constructs a stream that pipes data to a gpg process encrypting and signing.
     *
     * @param gpgHome        GnuPG home directory containing the secret key for signing.
     * @param output         Path to the output file, overwritten if present.
     * @param encryptionKeys Zero or more encryption key files in PGP format (see gpg --recipient-file)
     * @throws IOException if setting up the gpg process fails
     */
    GPGOutputStream(Path gpgHome, Path output, Path... encryptionKeys) throws IOException {
        super(null);
        statusTmp = Files.createTempFile(Paths.get(SystemProperties.getTempFilesPath()), "gpgstatus", ".tmp");
        final ProcessBuilder builder = new ProcessBuilder("/usr/bin/gpg");

        builder.command().add("--homedir");
        builder.command().add(gpgHome.toString());

        builder.command().addAll(Arrays.asList(DEFAULT_ARGS));

        builder.command().add("--output");
        builder.command().add(output.toString());

        builder.command().add("--status-file");
        builder.command().add(statusTmp.toString());

        if (encryptionKeys == null || encryptionKeys.length == 0) {
            builder.command().add("--default-recipient-self");
        } else {
            for (Path p : encryptionKeys) {
                builder.command().add("--recipient-file");
                builder.command().add(p.toString());
            }
        }

        //status output as well as actual output are directed to a file
        //discard any standard output/error so that the process does not block in any case.
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.to(Paths.get("/dev/null").toFile()));
        gpg = builder.start();
        if (!gpg.isAlive()) {
            throw new GPGException("gpg process failed, exit code " + gpg.exitValue(), null, gpg.exitValue());
        }
        out = gpg.getOutputStream();
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
    }

    /**
     * Closes the encryption stream and waits for the encryption to finish.
     * Tries to ensure that the gpg process is stopped even in a case of failure. No attempt to delete
     * the output file even in failure is made.
     *
     * @throws IOException if the output to the gpg process can not be closed.
     * @throws GPGException if gpg process does not stop or exits with and error code (!=0)
     */
    @Override
    public void close() throws IOException {
        try {
            if (out != null) {
                super.close();
            }
            //gpg encrypts on the fly, so it should finish almost immediately
            gpg.waitFor(1, TimeUnit.MINUTES);
            final List<String> status = Files.readAllLines(statusTmp, StandardCharsets.UTF_8);
            if (gpg.isAlive()) {
                log.debug("Encryption failed, GPG status: {}", status);
                throw new GPGException("Encryption failed, gpg process did not stop", status, -1);
            }
            if (gpg.exitValue() != 0) {
                log.debug("Encryption failed, GPG status: {}", status);
                throw new GPGException("Encryption failed, gpg process exit code: " + gpg.exitValue(), status,
                        gpg.exitValue());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (gpg.isAlive()) {
                //interrupted while waiting for gpg process to exit, signal failure
                throw new GPGException(e);
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

    @Getter
    static class GPGException extends IOException {
        private final String[] details;
        private final int exitCode;

        GPGException(String message, List<String> status, int exitCode) {
            super(message);
            this.details = status == null ? null : status.toArray(new String[0]);
            this.exitCode = exitCode;
        }

        GPGException(Throwable cause) {
            super(cause);
            this.details = null;
            this.exitCode = -1;
        }
    }

}
