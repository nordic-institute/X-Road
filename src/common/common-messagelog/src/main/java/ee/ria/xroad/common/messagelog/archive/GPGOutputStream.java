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
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Outputstream that pipes output to an external GnuPG process for signing and encryption
 */
@Slf4j
public class GPGOutputStream extends FilterOutputStream {

    private static final String[] DEFAULT_ARGS = {
            "--batch",
            "--no-tty",
            "--yes",
            "--encrypt",
            "--sign",
            // use only local keyring
            "--no-auto-key-locate",
            // always trust keys in keyring (expired or revoked keys are not used)
            "--trust-model", "always",
            // sane cipher setting overrides (disables per-key negotiation)
            "--compress-algo", "none",
            "--cipher-algo", "AES-256",
            "--digest-algo", "SHA256"
    };

    private final Process gpg;
    private final Path statusTmp;

    private final Object closeLock = new Object();
    private boolean closed = false;

    /**
     * Constructs a stream that pipes data to a gpg process for encrypting and signing.
     * @param gpgHome GnuPG home directory containing the secret key for signing.
     * @param output Path to the output file, overwritten if present.
     * @param encryptionKeys Zero or more encryption (recipient) key identifiers
     * @throws IOException if setting up the gpg process fails
     */
    public GPGOutputStream(Path gpgHome, Path output, Set<String> encryptionKeys) throws IOException {
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

        if (encryptionKeys == null || encryptionKeys.isEmpty()) {
            builder.command().add("--default-recipient-self");
        } else {
            for (String key : encryptionKeys) {
                builder.command().add("--recipient");
                builder.command().add(key);
            }
        }

        //status output as well as actual output are directed to a file
        //discard any standard output/error so that the process does not block in any case.
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.to(Paths.get("/dev/null").toFile()));
        gpg = builder.start();
        if (!gpg.isAlive()) {
            throw new GPGException("gpg process failed, exit code " + gpg.exitValue(), gpg.exitValue());
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
     * @throws GPGException If closing the stream or gpg process exits with error code
     */
    @Override
    public void close() throws GPGException {
        synchronized (closeLock) {
            if (closed) {
                return;
            }
            closed = true;
        }

        IOException suppressed = null;
        try {
            if (out != null) {
                try {
                    super.close();
                } catch (IOException e) {
                    suppressed = e;
                }
            }
            //gpg encrypts on the fly, so it should normally finish almost immediately
            gpg.waitFor(1, TimeUnit.MINUTES);

            List<String> status = getStatus();

            if (gpg.isAlive()) {
                log.error("Encryption failed, GPG status: {}", status);
                throw GPGException.of("Encryption failed, gpg process did not stop", -1, suppressed);
            }
            final int exitValue = gpg.exitValue();
            if (exitValue != 0 || suppressed != null) {
                log.error("Encryption failed: gpg exit code {}, status: {}", exitValue, status);
                throw GPGException.of("Encryption failed, gpg process exit code: " + exitValue, exitValue, suppressed);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw GPGException.of(e, suppressed);
        } finally {
            try {
                Files.deleteIfExists(statusTmp);
            } catch (IOException e) {
                //ignore
            }
            gpg.destroyForcibly();
            closed = true;
        }
    }

    private List<String> getStatus() {
        try {
            return Files.readAllLines(statusTmp, StandardCharsets.UTF_8);
        } catch (IOException ioe) {
            return Collections.emptyList();
        }
    }

    @Getter
    static class GPGException extends IOException {
        private final int exitCode;

        GPGException(String message, int exitCode) {
            super(message);
            this.exitCode = exitCode;
        }

        GPGException(Throwable cause) {
            super(cause);
            this.exitCode = -1;
        }

        static GPGException of(String message, int exitCode, Throwable suppressed) {
            final GPGException exception = new GPGException(message, exitCode);
            if (suppressed != null) {
                exception.addSuppressed(suppressed);
            }
            return exception;
        }

        static GPGException of(Throwable cause, Throwable suppressed) {
            final GPGException exception = new GPGException(cause);
            if (suppressed != null) {
                exception.addSuppressed(suppressed);
            }
            return exception;
        }
    }
}
