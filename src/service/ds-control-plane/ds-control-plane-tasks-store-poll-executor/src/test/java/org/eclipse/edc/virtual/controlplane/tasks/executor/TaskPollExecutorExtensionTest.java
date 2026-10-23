/*
 *  Copyright (c) 2026 Metaform Systems, Inc.
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Metaform Systems, Inc. - initial API and implementation
 *
 */

package org.eclipse.edc.virtual.controlplane.tasks.executor;

import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.properties.config.keys.DspConfigKeys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskPollExecutorExtensionTest {

    private static final String KEY = DspConfigKeys.TASK_POLL_WORKERS.key();
    private static final int DEFAULT_WORKERS = 4;

    @Mock
    private ServiceExtensionContext context;

    @Mock
    private Monitor monitor;

    @Test
    void aConfiguredWorkerCountIsUsedAsIs() {
        when(context.getSetting(KEY, DEFAULT_WORKERS)).thenReturn(8);

        assertThat(TaskPollExecutorExtension.resolveWorkerCount(context, monitor)).isEqualTo(8);
        verify(monitor, never()).warning(contains(KEY));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -4})
    void aWorkerCountBelowOneFallsBackToTheDefaultWithAWarning(int configured) {
        when(context.getSetting(KEY, DEFAULT_WORKERS)).thenReturn(configured);

        assertThat(TaskPollExecutorExtension.resolveWorkerCount(context, monitor)).isEqualTo(DEFAULT_WORKERS);
        verify(monitor).warning(contains(KEY));
    }
}
