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

package org.niis.xroad.auxiliaryservice.core.messagelog;

import ee.ria.xroad.common.util.process.BlockingProcessRunner;
import ee.ria.xroad.common.util.process.ExternalProcessRunner;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduler;
import org.jboss.logmanager.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.niis.xroad.auxiliaryservice.core.config.MessageLogEnabledProperties;
import org.niis.xroad.auxiliaryservice.core.config.MessageLogJobsProperties;
import org.niis.xroad.common.properties.NodeProperties;
import org.niis.xroad.globalconf.GlobalConfProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@ExtendWith({MockitoExtension.class})
class MessageLogArchiveJobTest {

    private static final String CRON = "0 0 0 * * ?";

    @Mock
    private MessageLogJobsProperties properties;

    @Mock
    private MessageLogEnabledProperties messageLogEnabledProperties;

    @Mock
    private BlockingProcessRunner blockingProcessRunner;

    @Mock
    private GlobalConfProvider globalConfProvider;

    @Mock
    private Scheduled.ApplicationNotRunning applicationNotRunning;

    @Test
    void registersJobOnPrimaryOrStandaloneNode() {
        var scheduler = mock(Scheduler.class);
        var jobDefinition = mock(Scheduler.JobDefinition.class, RETURNS_SELF);
        when(scheduler.newJob(any())).thenReturn(jobDefinition);
        when(messageLogEnabledProperties.isEnabled()).thenReturn(true);
        when(properties.archiveCron()).thenReturn(CRON);

        try (MockedStatic<NodeProperties> nodeProperties = mockStatic(NodeProperties.class)) {
            nodeProperties.when(NodeProperties::isSecondaryNode).thenReturn(false);

            new MessageLogArchiveJob(scheduler, properties, messageLogEnabledProperties, blockingProcessRunner,
                    globalConfProvider, applicationNotRunning).init(new StartupEvent());
        }

        verify(scheduler).newJob(any());
        verify(jobDefinition).setSkipPredicate(applicationNotRunning);
        verify(jobDefinition).schedule();
    }

    @Test
    void doesNotRegisterJobOnSecondaryNodeAndLogsOnce() {
        var scheduler = mock(Scheduler.class);
        when(messageLogEnabledProperties.isEnabled()).thenReturn(true);
        when(properties.archiveCron()).thenReturn(CRON);
        var job = new MessageLogArchiveJob(scheduler, properties, messageLogEnabledProperties, blockingProcessRunner,
                globalConfProvider, applicationNotRunning);

        List<LogRecord> logEvents;
        try (MockedStatic<NodeProperties> nodeProperties = mockStatic(NodeProperties.class)) {
            nodeProperties.when(NodeProperties::isSecondaryNode).thenReturn(true);
            logEvents = capturedLogEventsOf(() -> job.init(new StartupEvent()));
        }

        verifyNoInteractions(scheduler);
        assertThat(logEvents).hasSize(1);
        assertThat(logEvents.getFirst().getMessage()).contains("secondary").contains("disabled");
    }

    @Test
    void disabledJobLogsDisabledMessageOnPrimaryNode() {
        var scheduler = mock(Scheduler.class);
        when(messageLogEnabledProperties.isEnabled()).thenReturn(false);
        var job = new MessageLogArchiveJob(scheduler, properties, messageLogEnabledProperties, blockingProcessRunner,
                globalConfProvider, applicationNotRunning);

        var logEvents = capturedLogEventsOf(() -> job.init(new StartupEvent()));

        verifyNoInteractions(scheduler);
        assertThat(logEvents).hasSize(1);
        assertThat(logEvents.getFirst().getMessage()).contains("disabled");
    }

    @Test
    void disabledJobLogsDisabledMessageOnSecondaryNode() {
        var scheduler = mock(Scheduler.class);
        when(messageLogEnabledProperties.isEnabled()).thenReturn(false);
        var job = new MessageLogArchiveJob(scheduler, properties, messageLogEnabledProperties, blockingProcessRunner,
                globalConfProvider, applicationNotRunning);

        List<LogRecord> logEvents;
        try (MockedStatic<NodeProperties> nodeProperties = mockStatic(NodeProperties.class,
                withSettings().strictness(Strictness.LENIENT))) {
            nodeProperties.when(NodeProperties::isSecondaryNode).thenReturn(true);

            logEvents = capturedLogEventsOf(() -> job.init(new StartupEvent()));

            nodeProperties.verifyNoInteractions();
        }

        verifyNoInteractions(scheduler);
        assertThat(logEvents).hasSize(1);
        assertThat(logEvents.getFirst().getMessage()).contains("disabled");
    }

    @Test
    void executeOnShutdownArchivesOnPrimaryOrStandaloneNodeWhenEnabled() throws Exception {
        when(messageLogEnabledProperties.isEnabled()).thenReturn(true);
        when(properties.archiveCron()).thenReturn(CRON);
        when(blockingProcessRunner.executeAndThrowOnFailure(any(), any(), any()))
                .thenReturn(new ExternalProcessRunner.ProcessResult("archive", 0, List.of("output")));

        try (MockedStatic<NodeProperties> nodeProperties = mockStatic(NodeProperties.class)) {
            nodeProperties.when(NodeProperties::isSecondaryNode).thenReturn(false);

            new MessageLogArchiveJob(mock(Scheduler.class), properties, messageLogEnabledProperties, blockingProcessRunner,
                    globalConfProvider, applicationNotRunning).executeOnShutdown();
        }

        verify(blockingProcessRunner).executeAndThrowOnFailure(any(), any(), any());
    }

    @Test
    void executeOnShutdownDoesNotArchiveOnSecondaryNodeWhenEnabled() {
        when(messageLogEnabledProperties.isEnabled()).thenReturn(true);
        when(properties.archiveCron()).thenReturn(CRON);

        var job = new MessageLogArchiveJob(mock(Scheduler.class), properties, messageLogEnabledProperties, blockingProcessRunner,
                globalConfProvider, applicationNotRunning);

        List<LogRecord> logEvents;
        try (MockedStatic<NodeProperties> nodeProperties = mockStatic(NodeProperties.class)) {
            nodeProperties.when(NodeProperties::isSecondaryNode).thenReturn(true);
            logEvents = capturedLogEventsOf(job::executeOnShutdown);
        }

        verifyNoInteractions(blockingProcessRunner);
        assertThat(logEvents).hasSize(1);
        assertThat(logEvents.getFirst().getMessage()).contains("secondary").contains("disabled");
    }

    private static List<LogRecord> capturedLogEventsOf(Runnable action) {
        var logger = Logger.getLogger(MessageLogArchiveJob.class.getName());
        var records = new ArrayList<LogRecord>();
        var handler = new Handler() {
            @Override
            public void publish(LogRecord logRecord) {
                records.add(logRecord);
            }

            @Override
            public void flush() {
                // nothing to flush, records are captured synchronously
            }

            @Override
            public void close() {
                // no resources to release
            }
        };
        logger.addHandler(handler);
        logger.setLevel(java.util.logging.Level.INFO);
        try {
            action.run();
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(null);
        }
        return records;
    }

}
