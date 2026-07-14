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
package org.niis.xroad.cs.test.api;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.test.apitest.core.config.ApiTestCoreProperties;
import org.niis.xroad.test.apitest.core.container.BaseComposeSetup;
import org.testcontainers.containers.ComposeContainer;

import java.io.File;

import static org.testcontainers.containers.wait.strategy.Wait.forHealthcheck;
import static org.testcontainers.containers.wait.strategy.Wait.forListeningPort;
import static org.testcontainers.containers.wait.strategy.Wait.forLogMessage;

/**
 * Browserless Central Server test stack: CS admin service (with embedded postgres, openbao, and
 * co-located DS Issuer Service) + mock-server + mock-jwks-server.
 */
@Slf4j
@SuppressWarnings("checkstyle:magicnumber")
class CsApiTestContainerSetup extends BaseComposeSetup {

    static final String CS = "cs-admin-service";
    static final String MOCK_SERVER = "mock-server";
    static final String MOCK_JWKS_SERVER = "mock-jwks-server";

    private static final String COMPOSE_API_FILE = "compose.api.yaml";

    CsApiTestContainerSetup(ApiTestCoreProperties coreProperties) {
        super(coreProperties);
    }

    @Override
    protected String composeProjectName() {
        return "cs-api-";
    }

    @Override
    public ComposeContainer initEnv() {
        return new ComposeContainer(composeProjectName(),
                new File(coreProperties.resourceDir() + COMPOSE_API_FILE))
                .withExposedService(CS, Port.CS_ADMIN, forHealthcheck())
                .withExposedService(CS, Port.DB, forListeningPort())
                .withExposedService(MOCK_SERVER, Port.MOCK_SERVER,
                        forLogMessage(".*started on port: 1080.*", 1))
                .withLogConsumer(CS, createLogConsumer(CS))
                .withLogConsumer(MOCK_SERVER, createLogConsumer(MOCK_SERVER))
                .withLogConsumer(MOCK_JWKS_SERVER, createLogConsumer(MOCK_JWKS_SERVER));
    }
}
