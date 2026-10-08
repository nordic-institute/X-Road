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
package org.niis.xroad.proxy.dataplane;

import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.ScheduledExecution;
import io.quarkus.scheduler.Scheduler;
import org.eclipse.edc.connector.dataplane.spi.DataFlowStates;
import org.eclipse.edc.spi.result.StoreResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataFlowStateRetentionJobTest {

    @Mock
    Scheduler scheduler;
    @Mock
    DataFlowStateRetentionProperties properties;

    @SuppressWarnings("rawtypes")
    Scheduler.JobDefinition jobDefinition;
    FakeDataFlowStateStore store;
    DataFlowStateRetentionJob job;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setUp() {
        jobDefinition = mock(Scheduler.JobDefinition.class, Answers.RETURNS_SELF);
        when(scheduler.newJob(anyString())).thenReturn(jobDefinition);
        store = new FakeDataFlowStateStore();
        job = new DataFlowStateRetentionJob(scheduler, store, properties, new Scheduled.ApplicationNotRunning());
    }

    @Test
    void schedulesAQuarterOfTheRetentionPeriod() {
        when(properties.retention()).thenReturn(Duration.ofHours(6));

        job.init();

        verify(jobDefinition).setInterval(Duration.ofHours(6).dividedBy(4).toString());
        verify(jobDefinition).schedule();
    }

    @Test
    @SuppressWarnings("unchecked")
    void scheduledTaskPrunesRowsOlderThanTheRetentionWindow() {
        var retention = Duration.ofHours(6);
        when(properties.retention()).thenReturn(retention);

        job.init();

        var taskCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(jobDefinition).setTask(taskCaptor.capture());

        taskCaptor.getValue().accept(mock(ScheduledExecution.class));

        assertThat(store.pruneCalls).containsExactly(retention);
    }

    private static final class FakeDataFlowStateStore implements DataFlowStateStore {

        private final List<Duration> pruneCalls = new ArrayList<>();

        @Override
        public StoreResult<Void> save(String flowId, DataFlowStates state) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<DataFlowStates> find(String flowId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int pruneTerminal(Duration retention) {
            pruneCalls.add(retention);
            return 0;
        }
    }
}
