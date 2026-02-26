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

import io.cucumber.java.en.Then;
import org.niis.xroad.ss.test.addons.api.FeignHealthApi;
import org.springframework.beans.factory.annotation.Autowired;

import static org.niis.xroad.ss.test.SsSystemTestContainerSetup.BACKUP_MANAGER;
import static org.niis.xroad.ss.test.SsSystemTestContainerSetup.CONFIGURATION_CLIENT;
import static org.niis.xroad.ss.test.SsSystemTestContainerSetup.OP_MONITOR;
import static org.niis.xroad.ss.test.SsSystemTestContainerSetup.PROXY;
import static org.niis.xroad.ss.test.SsSystemTestContainerSetup.SIGNER;

/**
 * Step definitions for MicroProfile Health readiness check tests.
 */
@SuppressWarnings("SpringJavaAutowiredMembersInspection")
public class ReadinessCheckStepDefs {

    @Autowired
    private HealthCheckAssertions healthCheckAssertions;

    @Then("proxy readiness check is UP")
    public void proxyReadinessCheckIsUp() {
        healthCheckAssertions.assertOverallStatusIsUp(PROXY, FeignHealthApi::getReadiness);
    }

    @Then("signer readiness check is UP")
    public void signerReadinessCheckIsUp() {
        healthCheckAssertions.assertOverallStatusIsUp(SIGNER, FeignHealthApi::getReadiness);
    }

    @Then("configuration-client readiness check is UP")
    public void configurationClientReadinessCheckIsUp() {
        healthCheckAssertions.assertOverallStatusIsUp(CONFIGURATION_CLIENT, FeignHealthApi::getReadiness);
    }

    @Then("op-monitor readiness check is UP")
    public void opMonitorReadinessCheckIsUp() {
        healthCheckAssertions.assertOverallStatusIsUp(OP_MONITOR, FeignHealthApi::getReadiness);
    }

    @Then("backup-manager readiness check is UP")
    public void backupManagerReadinessCheckIsUp() {
        healthCheckAssertions.assertOverallStatusIsUp(BACKUP_MANAGER, FeignHealthApi::getReadiness);
    }

    @Then("{string} service readiness check is UP")
    public void serviceReadinessCheckIsUp(String serviceName) {
        healthCheckAssertions.assertOverallStatusIsUp(serviceName, FeignHealthApi::getReadiness);
    }

    @Then("{string} service readiness check {string} has status {string}")
    public void serviceReadinessCheckHasStatus(String serviceName, String checkName, String expectedStatus) {
        healthCheckAssertions.assertCheckHasStatus(serviceName, checkName, expectedStatus, FeignHealthApi::getReadiness);
    }
}
