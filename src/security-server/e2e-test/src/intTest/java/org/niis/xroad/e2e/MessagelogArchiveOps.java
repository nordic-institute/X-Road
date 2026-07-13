/*
 * The MIT License
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
package org.niis.xroad.e2e;

/**
 * Environment-neutral access to the message log archiver, available in both Compose and LXD modes.
 */
public interface MessagelogArchiveOps {

    /**
     * Triggers the message log archiver CLI on the given environment with the given command
     * ({@code "archive <instanceIdentifier>"} or {@code "cleanup"}) and blocks until it completes.
     * The CLI always exits 0 regardless of outcome (it logs and swallows exceptions internally),
     * so implementations verify success from the process's own log output and throw if the
     * operation did not report success.
     */
    void triggerMessageLogCommand(String env, String command);

    /**
     * Packages every produced archive file (named {@code mlog-*}) under the environment's message
     * log archive directory into a single {@code messagelog-archives.tar.gz} (entries rooted at
     * {@code ./}, matching {@code tar czf ... -C <archiveDir> .}) and downloads it into
     * {@code localDir} (created if absent).
     */
    void downloadMessageLogArchives(String env, String localDir);

    /**
     * Decrypts every archive file on the environment's message log archive directory whose name
     * starts with {@code filePrefix} — operating directly on the archiver's output, not on a prior
     * local download — using the given PGP private key and passphrase, packages the decrypted
     * (still-zipped, no-longer-encrypted) files into {@code messagelog-archives.tar.gz} and
     * downloads it into {@code outputDir} (created if absent). Decryption errors for individual
     * files are ignored, mirroring the compose fixture script {@code decrypt-archives.sh}. Returns
     * the number of files considered for decryption (callers compare this against the number of
     * files that land in the downloaded tarball).
     */
    int decryptArchives(String env, String filePrefix, String keyId, String passphrase, String outputDir);
}
