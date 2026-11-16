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

package org.niis.xroad.auxiliaryservice.core.backup.job;

import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.niis.xroad.auxiliaryservice.core.backup.FileSystemBackupHandler;
import org.niis.xroad.auxiliaryservice.core.config.BackupProperties;
import org.niis.xroad.common.properties.NodeProperties;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@ExtendWith({MockitoExtension.class})
class OldAutoBackupsRemovalJobTest {

    private static final String CRON = "0 0 0 * * ?";

    @Mock
    private BackupProperties backupProperties;

    @Mock
    private FileSystemBackupHandler fileSystemBackupHandler;

    private final Scheduled.ApplicationNotRunning applicationNotRunning = mock(Scheduled.ApplicationNotRunning.class);

    @Test
    void registersJobOnPrimaryNode() {
        var scheduler = mock(Scheduler.class);
        var jobDefinition = mock(Scheduler.JobDefinition.class, RETURNS_SELF);
        when(scheduler.newJob(any())).thenReturn(jobDefinition);
        when(backupProperties.autoBackupDeleteOldBackupsCron()).thenReturn(CRON);

        try (MockedStatic<NodeProperties> nodeProperties = mockStatic(NodeProperties.class)) {
            nodeProperties.when(NodeProperties::isSecondaryNode).thenReturn(false);

            new OldAutoBackupsRemovalJob(scheduler, backupProperties, fileSystemBackupHandler, applicationNotRunning).init();
        }

        verify(scheduler).newJob(any());
        verify(jobDefinition).setSkipPredicate(applicationNotRunning);
        verify(jobDefinition).schedule();
    }

    @Test
    void registersJobOnStandaloneNode() {
        var scheduler = mock(Scheduler.class);
        var jobDefinition = mock(Scheduler.JobDefinition.class, RETURNS_SELF);
        when(scheduler.newJob(any())).thenReturn(jobDefinition);
        when(backupProperties.autoBackupDeleteOldBackupsCron()).thenReturn(CRON);

        try (MockedStatic<NodeProperties> nodeProperties = mockStatic(NodeProperties.class)) {
            nodeProperties.when(NodeProperties::isSecondaryNode).thenReturn(false);

            new OldAutoBackupsRemovalJob(scheduler, backupProperties, fileSystemBackupHandler, applicationNotRunning).init();
        }

        verify(scheduler).newJob(any());
        verify(jobDefinition).schedule();
    }

    @Test
    void doesNotRegisterJobOnSecondaryNodeAndLogsOnce() {
        var scheduler = mock(Scheduler.class);
        when(backupProperties.autoBackupDeleteOldBackupsCron()).thenReturn(CRON);

        try (MockedStatic<NodeProperties> nodeProperties = mockStatic(NodeProperties.class)) {
            nodeProperties.when(NodeProperties::isSecondaryNode).thenReturn(true);
            new OldAutoBackupsRemovalJob(scheduler, backupProperties, fileSystemBackupHandler, applicationNotRunning).init();
        }

        verifyNoInteractions(scheduler);
    }

    @Test
    void disabledJobLogsDisabledMessageOnPrimaryNodeAndNeverReadsNodeRole() {
        var scheduler = mock(Scheduler.class);

        try (MockedStatic<NodeProperties> nodeProperties = mockStatic(NodeProperties.class,
                withSettings().strictness(Strictness.LENIENT))) {
            nodeProperties.when(NodeProperties::isSecondaryNode).thenReturn(false);
            new OldAutoBackupsRemovalJob(scheduler, backupProperties, fileSystemBackupHandler, applicationNotRunning).init();

            nodeProperties.verifyNoInteractions();
        }

        verifyNoInteractions(scheduler);
    }

    @Test
    void disabledJobLogsDisabledMessageOnSecondaryNodeAndNeverReadsNodeRole() {
        var scheduler = mock(Scheduler.class);

        try (MockedStatic<NodeProperties> nodeProperties = mockStatic(NodeProperties.class,
                withSettings().strictness(Strictness.LENIENT))) {
            nodeProperties.when(NodeProperties::isSecondaryNode).thenReturn(true);
            new OldAutoBackupsRemovalJob(scheduler, backupProperties, fileSystemBackupHandler, applicationNotRunning).init();

            nodeProperties.verifyNoInteractions();
        }

        verifyNoInteractions(scheduler);
    }

}
