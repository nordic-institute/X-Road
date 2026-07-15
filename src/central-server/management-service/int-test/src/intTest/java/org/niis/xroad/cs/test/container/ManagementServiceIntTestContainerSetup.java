/*
 * The MIT License
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
package org.niis.xroad.cs.test.container;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.mockserver.client.MockServerClient;
import org.niis.xroad.cs.test.service.ContainerDatabaseProvider;
import org.niis.xroad.cs.test.service.LiquibaseExecutor;
import org.niis.xroad.test.apitest.core.config.ApiTestCoreProperties;
import org.niis.xroad.test.apitest.core.container.BaseComposeSetup;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.time.Duration;

/**
 * Central Server management-service test stack: the CS admin-service container (management endpoint
 * under test) and a MockServer container standing in for the real Central Server admin API. Boots once
 * per JVM launcher session; the centerui schema is initialised via Liquibase once the database is up.
 */
@Slf4j
public class ManagementServiceIntTestContainerSetup extends BaseComposeSetup {

    private static final Duration CS_STARTUP_TIMEOUT = Duration.ofSeconds(90);
    private static final String COMPOSE_FILE = "compose.intTest.yaml";

    public static final String CS = "cs-admin-service";
    public static final String MOCKSERVER = "mock-server";
    public static final String POSTGRES = CS;

    private MockServerClient mockServerClient;

    public ManagementServiceIntTestContainerSetup(ApiTestCoreProperties coreProperties) {
        super(coreProperties);
    }

    @Override
    protected String composeProjectName() {
        return "cs-";
    }

    @Override
    protected ComposeContainer initEnv() {
        return new ComposeContainer(composeProjectName(), new File(coreProperties.resourceDir() + COMPOSE_FILE))
                .withExposedService(CS, Port.API, Wait.forHealthcheck().withStartupTimeout(CS_STARTUP_TIMEOUT))
                .withExposedService(POSTGRES, Port.DB, Wait.forListeningPort())
                .withExposedService(MOCKSERVER, Port.MOCKSERVER, Wait.forLogMessage(".*started on port: 1080.*", 1))
                .withLogConsumer(CS, createLogConsumer(CS));
    }

    @Override
    protected void onPostStart() {
        log.info("Initializing centerui schema for tests...");
        new LiquibaseExecutor(new ContainerDatabaseProvider(this)).executeChangesets();
    }

    /**
     * Base URL of the CS admin-service container hosting the management-request endpoint under test.
     */
    public String baseUrl() {
        var mapping = getContainerMapping(CS, Port.API);
        return "http://%s:%d".formatted(mapping.host(), mapping.port());
    }

    /**
     * Full URL of the SOAP management-request endpoint.
     */
    public String managementServiceUrl() {
        return baseUrl() + "/managementservice/manage";
    }

    /**
     * Lazily-created client for the MockServer container standing in for the Central Server admin API.
     */
    public MockServerClient mockServerClient() {
        if (mockServerClient == null) {
            var mapping = getContainerMapping(MOCKSERVER, Port.MOCKSERVER);
            mockServerClient = new MockServerClient(mapping.host(), mapping.port());
        }
        return mockServerClient;
    }

    @UtilityClass
    public static final class Port {
        public static final int DB = 5432;
        public static final int API = 4002;
        public static final int MOCKSERVER = 1080;
    }
}
