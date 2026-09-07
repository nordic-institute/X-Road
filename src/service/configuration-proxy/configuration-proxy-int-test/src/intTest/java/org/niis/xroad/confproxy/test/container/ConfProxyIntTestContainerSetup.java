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
package org.niis.xroad.confproxy.test.container;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.test.apitest.core.config.ApiTestCoreProperties;
import org.niis.xroad.test.apitest.core.container.BaseComposeSetup;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;

/**
 * Configuration-proxy test stack: the configuration-proxy service under test, its signer dependency, and
 * its own PostgreSQL database, plus a local nginx standing in for the dev global-configuration source
 * ({@code xrd-cs}). Built from the product's own dev-stack {@code compose.main.yaml} (copied from
 * {@code development/docker/configuration-proxy}) overridden by {@code compose.intTest.yaml}. Boots once
 * per JVM launcher session.
 */
@Slf4j
public class ConfProxyIntTestContainerSetup extends BaseComposeSetup {

    public static final String CONFIGURATION_PROXY = "configuration-proxy";
    public static final String SIGNER = "signer";
    public static final String DB_CONFPROXY = "db-confproxy";

    private static final String COMPOSE_MAIN_FILE = "compose.main.yaml";
    private static final String COMPOSE_INTTEST_FILE = "compose.intTest.yaml";

    public ConfProxyIntTestContainerSetup(ApiTestCoreProperties coreProperties) {
        super(coreProperties);
    }

    @Override
    protected String composeProjectName() {
        return "confproxy-";
    }

    @Override
    protected ComposeContainer initEnv() {
        return new ComposeContainer(composeProjectName(),
                new File(coreProperties.resourceDir() + COMPOSE_MAIN_FILE),
                new File(coreProperties.resourceDir() + COMPOSE_INTTEST_FILE))
                .withExposedService(CONFIGURATION_PROXY, Port.HTTP, Wait.forHealthcheck())
                .withExposedService(CONFIGURATION_PROXY, Port.HTTPS, Wait.forHealthcheck())
                .withExposedService(DB_CONFPROXY, Port.DB, Wait.forListeningPort())
                .withLogConsumer(CONFIGURATION_PROXY, createLogConsumer(CONFIGURATION_PROXY))
                .withLogConsumer(SIGNER, createLogConsumer(SIGNER));
    }

    /**
     * Returns the base URL ({@code http://host:port/api}) of the configuration-proxy REST API.
     */
    public String apiBaseUrl() {
        var mapping = getContainerMapping(CONFIGURATION_PROXY, Port.HTTP);
        return "http://%s:%d/api".formatted(mapping.host(), mapping.port());
    }

    @UtilityClass
    public static final class Port {
        public static final int HTTP = 4099;
        public static final int HTTPS = 4000;
        public static final int DB = 5432;
    }
}
