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
package org.niis.xroad.ss.test.addons.glue;

import io.cucumber.java.en.Step;
import org.niis.xroad.ss.test.addons.api.FeignHealthApi;
import org.springframework.beans.factory.annotation.Autowired;

import static org.niis.xroad.ss.test.SsSystemTestContainerSetup.BACKUP_MANAGER;
import static org.niis.xroad.ss.test.SsSystemTestContainerSetup.CONFIGURATION_CLIENT;
import static org.niis.xroad.ss.test.SsSystemTestContainerSetup.OP_MONITOR;
import static org.niis.xroad.ss.test.SsSystemTestContainerSetup.PROXY;
import static org.niis.xroad.ss.test.SsSystemTestContainerSetup.SIGNER;

/**
 * Step definitions for MicroProfile Health liveness check tests.
 */
@SuppressWarnings("SpringJavaAutowiredMembersInspection")
public class LivenessCheckStepDefs {

    @Autowired
    private HealthCheckAssertions healthCheckAssertions;

    @Step("proxy liveness check is UP")
    public void proxyLivenessCheckIsUp() {
        healthCheckAssertions.assertOverallStatusIsUp(PROXY, FeignHealthApi::getLiveness);
    }

    @Step("signer liveness check is UP")
    public void signerLivenessCheckIsUp() {
        healthCheckAssertions.assertOverallStatusIsUp(SIGNER, FeignHealthApi::getLiveness);
    }

    @Step("configuration-client liveness check is UP")
    public void configurationClientLivenessCheckIsUp() {
        healthCheckAssertions.assertOverallStatusIsUp(CONFIGURATION_CLIENT, FeignHealthApi::getLiveness);
    }

    @Step("op-monitor liveness check is UP")
    public void opMonitorLivenessCheckIsUp() {
        healthCheckAssertions.assertOverallStatusIsUp(OP_MONITOR, FeignHealthApi::getLiveness);
    }

    @Step("backup-manager liveness check is UP")
    public void backupManagerLivenessCheckIsUp() {
        healthCheckAssertions.assertOverallStatusIsUp(BACKUP_MANAGER, FeignHealthApi::getLiveness);
    }

    @Step("{string} service liveness check {string} has status {string}")
    public void serviceLivenessCheckHasStatus(String serviceName, String checkName, String expectedStatus) {
        healthCheckAssertions.assertCheckHasStatus(serviceName, checkName, expectedStatus, FeignHealthApi::getLiveness);
    }
}
