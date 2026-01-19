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
package org.niis.xroad.cli;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ArchiveHashChainVerifierTest {
    private static final String MESSAGE_ARCHIVE_0_FILENAME = "build/resources/test/message-archive-0.zip";
    private static final String MESSAGE_ARCHIVE_1_FILENAME = "build/resources/test/message-archive-1.zip";
    private final ArchiveHashChainVerifier verifier = new ArchiveHashChainVerifier();

    @Test
    void shouldSucceedOnValidHashChain() {
        String validHashChain =
                "9011ab557706b5050584d6888af7b390e5350f4edad296bd7a582aa51732aac"
                        + "7f49a4ec44dd361027945ade0de8b8cf607fb7b7d11f17b0560d44b965c358eaa";
        try {
            verifier.run(new String[]{MESSAGE_ARCHIVE_1_FILENAME, validHashChain});
        } catch (Exception e) {
            Assertions.fail(e);
        }
    }

    @Test
    void shouldSucceedOnValidHFirst() {
        try {
            verifier.run(new String[]{MESSAGE_ARCHIVE_0_FILENAME, "-f"});
        } catch (Exception e) {
            Assertions.fail(e);
        }
    }

    @Test
    void shouldFailOnMissingArgs() {
        try {
            verifier.run(new String[]{});
            Assertions.fail();
        } catch (Exception e) {
            Assertions.assertTrue(e instanceof ArchiveHashChainVerifier.InputErrorException);
        }
    }

    @Test
    void shouldFailOnMissingPrevHashArg() {
        try {
            verifier.run(new String[]{MESSAGE_ARCHIVE_1_FILENAME});
            Assertions.fail();
        } catch (Exception e) {
            Assertions.assertTrue(e instanceof ArchiveHashChainVerifier.InputErrorException);
        }
    }

    @Test
    void shouldFailOnInvalidPreviousHash() {
        String validHashChain =
                "4145ab557706b5050584d6888af7b390e5350f4edad296bd7a582aa51732"
                        + "aac7f49a4ec44dd361027945ade0de8b8cf607fb7b7d11f17b0560d44b965c358eaa";
        try {
            verifier.run(new String[]{MESSAGE_ARCHIVE_1_FILENAME, validHashChain});
            Assertions.fail();
        } catch (Exception e) {
            Assertions.assertTrue(e instanceof MessageArchiveExtractor.InvalidLogArchiveException);
        }
    }
}
