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

plugins {
    id("xroad.java-conventions")
}

dependencies {
    api(libs.edc.spi.core)
    api(libs.edc.spi.controlplane)

    testImplementation(libs.awaitility)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.jupiter)
    testImplementation(libs.edc.junit)
    testImplementation(libs.edc.lib.core)
    testImplementation(libs.edc.lib.controlplane)
    testImplementation(libs.edc.core.tasks)
    testImplementation(libs.edc.core.controlplane)
    testImplementation(libs.edc.core.controlplane.contract)
    testImplementation(libs.edc.core.controlplane.contract.tasks)
    testImplementation(libs.edc.core.controlplane.transfer)
    testImplementation(libs.edc.core.controlplane.transfer.tasks)
    testImplementation(testFixtures(libs.edc.spi.controlplane))
    testImplementation(libs.testcontainers.junit)
}

