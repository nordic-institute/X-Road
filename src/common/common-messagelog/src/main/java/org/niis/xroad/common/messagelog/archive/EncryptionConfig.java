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
package org.niis.xroad.common.messagelog.archive;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * Strategy interface for archive encryption configuration.
 * Provides factory method to create encryption output streams.
 */
public interface EncryptionConfig {

    /**
     * Creates an output stream that encrypts data written to it.
     *
     * <p>This is a factory method that creates the appropriate encryption stream
     * based on the configuration (GPG, Vault/BouncyCastle, or disabled).
     *
     * @param outputFile Path to the output file where encrypted data will be written
     * @param tempFilesPath Path to temporary files directory (may be used by some implementations)
     * @return OutputStream that encrypts data
     * @throws IOException if stream creation fails
     * @throws UnsupportedOperationException if encryption is disabled
     */
    OutputStream createEncryptionStream(Path outputFile, String tempFilesPath) throws IOException;

    /**
     * Gets encryption member information for diagnostics.
     *
     * @return List of encryption members, or empty list if not applicable
     */
    List<EncryptionMember> encryptionMembers();

}
