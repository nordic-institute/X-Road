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

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.test.framework.core.config.TestFrameworkCoreProperties;
import org.niis.xroad.test.framework.core.container.BaseComposeSetup;
import org.springframework.stereotype.Service;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.time.Duration;

import static ee.ria.xroad.common.PortNumbers.SIGNER_GRPC_PORT;

@Slf4j
@Service
public class SoftTokenSignerIntTestContainerSetup extends BaseComposeSetup {
    private static final Duration SIGNER_STARTUP_TIMEOUT = Duration.ofSeconds(45);
    private static final Duration SOFTTOKEN_SIGNER_STARTUP_TIMEOUT = Duration.ofSeconds(45);

    public static final String SIGNER = "signer";
    public static final String SOFTTOKEN_SIGNER = "softtoken-signer";
    public static final String TESTCA = "testca";
    public static final String DB_SERVERCONF = "db-serverconf";

    private static final String COMPOSE_FILE = "compose.intTest.yaml";

    public SoftTokenSignerIntTestContainerSetup(TestFrameworkCoreProperties coreProperties) {
        super(coreProperties);
    }

    /**
     * Get the Docker Compose environment.
     * @return the compose container environment
     */
    public ComposeContainer getEnv() {
        return env;
    }

    @Override
    public ComposeContainer initEnv() {
        return new ComposeContainer("softtoken-signer-", new File(coreProperties.resourceDir() + COMPOSE_FILE))
                .withExposedService(SIGNER, SIGNER_GRPC_PORT, Wait.forHealthcheck().withStartupTimeout(SIGNER_STARTUP_TIMEOUT))
                .withExposedService(SOFTTOKEN_SIGNER, Port.SOFTTOKEN_SIGNER_GRPC,
                        Wait.forHealthcheck().withStartupTimeout(SOFTTOKEN_SIGNER_STARTUP_TIMEOUT))
                .withExposedService(DB_SERVERCONF, Port.DB, Wait.forListeningPort())
                .withExposedService(TESTCA, Port.TEST_CA, Wait.forLogMessage(".*nginx entered RUNNING state.*", 1))
                .withLogConsumer(SOFTTOKEN_SIGNER, createLogConsumer(SOFTTOKEN_SIGNER));
    }

    /**
     * Port constants for containerized services.
     */
    @UtilityClass
    public final class Port {
        public static final int DB = 5432;
        public static final int TEST_CA = 8888;
        public static final int SOFTTOKEN_SIGNER_GRPC = 5561;
    }
}
