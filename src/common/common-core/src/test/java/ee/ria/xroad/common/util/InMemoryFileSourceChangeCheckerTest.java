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
package ee.ria.xroad.common.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InMemoryFileSourceChangeCheckerTest {

    private FileSource<InMemoryFile> sourceMock;
    private InMemoryFileSourceChangeChecker checker;

    @BeforeEach
    void setUp() {
        sourceMock = mock(FileSource.class);
    }

    @Test
    void testHasChangedWhenFileChanged() throws Exception {
        var initialChecksum = "initialChecksum";
        var newChecksum = "newChecksum";

        var fileMock = new InMemoryFile("", newChecksum);
        when(sourceMock.getFile()).thenReturn(Optional.of(fileMock));

        checker = new InMemoryFileSourceChangeChecker(sourceMock, initialChecksum);

        assertTrue(checker.hasChanged());
    }

    @Test
    void testHasChangedWhenFileNotChanged() throws Exception {
        var checksum = "sameChecksum";

        var fileMock = new InMemoryFile("", checksum);
        when(sourceMock.getFile()).thenReturn(Optional.of(fileMock));

        checker = new InMemoryFileSourceChangeChecker(sourceMock, checksum);

        assertFalse(checker.hasChanged());
    }

    @Test
    void testHasChangedWhenFileNotPresent() throws Exception {
        when(sourceMock.getFile()).thenReturn(Optional.empty());

        checker = new InMemoryFileSourceChangeChecker(sourceMock, "anyChecksum");

        assertTrue(checker.hasChanged());
    }
}
