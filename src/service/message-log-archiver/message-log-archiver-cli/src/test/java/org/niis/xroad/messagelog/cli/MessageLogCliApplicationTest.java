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
package org.niis.xroad.messagelog.cli;

import org.junit.jupiter.api.Test;
import org.niis.xroad.messagelog.archiver.core.MessageLogArchiverService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Verifies that {@link MessageLogCliApplication} maps operation outcome to the process exit code:
 * 0 when the invoked {@link MessageLogArchiverService} operation succeeds, non-zero when it throws.
 */
class MessageLogCliApplicationTest {

    private static final String INSTANCE_IDENTIFIER = "INSTANCE";

    private final MessageLogArchiverService messageLogArchiverService = mock(MessageLogArchiverService.class);
    private final MessageLogCliApplication application = new MessageLogCliApplication(messageLogArchiverService);

    @Test
    void archiveReturnsZeroOnSuccess() {
        int exitCode = application.run("archive", INSTANCE_IDENTIFIER);

        verify(messageLogArchiverService).triggerArchival(INSTANCE_IDENTIFIER);
        assertEquals(0, exitCode);
    }

    @Test
    void archiveReturnsNonZeroWhenOperationFails() {
        doThrow(new RuntimeException("archive boom")).when(messageLogArchiverService).triggerArchival(INSTANCE_IDENTIFIER);

        int exitCode = application.run("archive", INSTANCE_IDENTIFIER);

        assertNotEquals(0, exitCode);
    }

    @Test
    void cleanupReturnsZeroOnSuccess() {
        int exitCode = application.run("cleanup");

        verify(messageLogArchiverService).triggerCleanup();
        assertEquals(0, exitCode);
    }

    @Test
    void cleanupReturnsNonZeroWhenOperationFails() {
        doThrow(new RuntimeException("cleanup boom")).when(messageLogArchiverService).triggerCleanup();

        int exitCode = application.run("cleanup");

        assertNotEquals(0, exitCode);
    }
}
