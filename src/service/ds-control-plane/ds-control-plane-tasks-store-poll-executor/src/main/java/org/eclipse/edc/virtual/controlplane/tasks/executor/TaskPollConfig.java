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

import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.runtime.metamodel.annotation.Settings;

@Settings
public record TaskPollConfig(
        @Setting(key = "edc.tasks.poll.shutdown-timeout",
                description = "Shutdown timeout for the task poller", defaultValue = "10")
        int shutdownTimeout,
        @Setting(key = "edc.tasks.poll.max-retry",
                description = "Max retries for task execution failure on transient errors", defaultValue = "3")
        int maxRetries
) {

}
