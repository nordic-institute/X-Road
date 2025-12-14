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

package org.niis.xroad.softtoken.signer.test;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.niis.xroad.softtoken.signer.test.container.SofttokenSignerIntTestConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static ee.ria.xroad.common.PortNumbers.SIGNER_GRPC_PORT;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke tests for softtoken-signer integration test environment.
 * Verifies that all containers start successfully and are healthy.
 */
@Slf4j
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = SofttokenSignerIntTestConfiguration.class)
class SofttokenSignerIntTest {

    @Autowired
    private SofttokenSignerIntTestContainerSetup containerSetup;

    /**
     * Verifies that the test environment starts successfully.
     * This test confirms that:
     * - All 4 containers (signer, softtoken-signer, database, testca) start
     * - Health checks pass for all services
     * - Container orchestration is working correctly
     */
    @Test
    void environmentStartsSuccessfully() {
        log.info("Verifying test environment started successfully");

        assertNotNull(containerSetup, "Container setup should be autowired");
        assertNotNull(containerSetup.getEnv(), "Compose environment should be initialized");

        // Verify all containers are running
        var signerContainer = containerSetup.getEnv().getContainerByServiceName(
                SofttokenSignerIntTestContainerSetup.SIGNER);
        assertTrue(signerContainer.isPresent(), "Signer container should be present");
        assertTrue(signerContainer.get().isRunning(), "Signer container should be running");

        var softtokenSignerContainer = containerSetup.getEnv().getContainerByServiceName(
                SofttokenSignerIntTestContainerSetup.SOFTTOKEN_SIGNER);
        assertTrue(softtokenSignerContainer.isPresent(), "Softtoken-signer container should be present");
        assertTrue(softtokenSignerContainer.get().isRunning(), "Softtoken-signer container should be running");

        var dbContainer = containerSetup.getEnv().getContainerByServiceName(
                SofttokenSignerIntTestContainerSetup.DB_SERVERCONF);
        assertTrue(dbContainer.isPresent(), "Database container should be present");
        assertTrue(dbContainer.get().isRunning(), "Database container should be running");

        var testcaContainer = containerSetup.getEnv().getContainerByServiceName(
                SofttokenSignerIntTestContainerSetup.TESTCA);
        assertTrue(testcaContainer.isPresent(), "TestCA container should be present");
        assertTrue(testcaContainer.get().isRunning(), "TestCA container should be running");

        // Verify we can get connection details
        var signerMapping = containerSetup.getContainerMapping(
                SofttokenSignerIntTestContainerSetup.SIGNER, SIGNER_GRPC_PORT);
        assertNotNull(signerMapping, "Signer connection mapping should be available");
        assertTrue(signerMapping.port() > 0, "Signer port should be mapped");

        var softtokenSignerMapping = containerSetup.getContainerMapping(
                SofttokenSignerIntTestContainerSetup.SOFTTOKEN_SIGNER,
                SofttokenSignerIntTestContainerSetup.Port.SOFTTOKEN_SIGNER_GRPC);
        assertNotNull(softtokenSignerMapping, "Softtoken-signer connection mapping should be available");
        assertTrue(softtokenSignerMapping.port() > 0, "Softtoken-signer port should be mapped");

        log.info("Test environment verified successfully");
        log.info("Signer available at: {}:{}", signerMapping.host(), signerMapping.port());
        log.info("Softtoken-signer available at: {}:{}", softtokenSignerMapping.host(), softtokenSignerMapping.port());
    }
}
