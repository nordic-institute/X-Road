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
package org.niis.xroad.messagelog.archiver.core;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.messagelog.MessageLogEncryptionProperties;
import org.niis.xroad.messagelog.archiver.core.config.MessageLogArchiverProperties;
import org.slf4j.LoggerFactory;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Verifies that {@link MessageLogArchiverService} lets operation failures propagate to its caller
 * instead of swallowing them, and that the success log markers callers grep for stay verbatim.
 */
class MessageLogArchiverServiceTest {

    private static final String INSTANCE_IDENTIFIER = "INSTANCE";
    private static final String ARCHIVAL_SUCCESS_MARKER = "Archival operation completed successfully";
    private static final String CLEANUP_SUCCESS_MARKER = "Cleanup operation completed successfully";

    private final LogArchiver logArchiver = mock(LogArchiver.class);
    private final LogCleaner logCleaner = mock(LogCleaner.class);
    private final MessageLogArchiverProperties archiverProperties = mock(MessageLogArchiverProperties.class);
    private final MessageLogEncryptionProperties encryptionProperties = mock(MessageLogEncryptionProperties.class);

    private final MessageLogArchiverService service =
            new MessageLogArchiverService(logArchiver, logCleaner, archiverProperties, encryptionProperties);

    private Logger serviceLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        serviceLogger = (Logger) LoggerFactory.getLogger(MessageLogArchiverService.class);
        appender = new ListAppender<>();
        appender.start();
        serviceLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        serviceLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void triggerArchivalPropagatesUnderlyingFailure() {
        RuntimeException failure = new RuntimeException("archive boom");
        doThrow(failure).when(logArchiver).execute(INSTANCE_IDENTIFIER, archiverProperties, encryptionProperties);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.triggerArchival(INSTANCE_IDENTIFIER));

        assertEquals(failure, thrown);
        assertTrue(loggedMessages().noneMatch(ARCHIVAL_SUCCESS_MARKER::equals));
    }

    @Test
    void triggerArchivalLogsSuccessMarkerOnSuccess() {
        service.triggerArchival(INSTANCE_IDENTIFIER);

        verify(logArchiver).execute(INSTANCE_IDENTIFIER, archiverProperties, encryptionProperties);
        assertTrue(loggedMessages().anyMatch(ARCHIVAL_SUCCESS_MARKER::equals));
    }

    @Test
    void triggerCleanupPropagatesUnderlyingFailure() {
        RuntimeException failure = new RuntimeException("cleanup boom");
        doThrow(failure).when(logCleaner).execute(archiverProperties);

        RuntimeException thrown = assertThrows(RuntimeException.class, service::triggerCleanup);

        assertEquals(failure, thrown);
        assertTrue(loggedMessages().noneMatch(CLEANUP_SUCCESS_MARKER::equals));
    }

    @Test
    void triggerCleanupLogsSuccessMarkerOnSuccess() {
        service.triggerCleanup();

        verify(logCleaner).execute(archiverProperties);
        assertTrue(loggedMessages().anyMatch(CLEANUP_SUCCESS_MARKER::equals));
    }

    private Stream<String> loggedMessages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage);
    }
}
