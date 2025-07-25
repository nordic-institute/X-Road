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
package org.niis.xroad.opmonitor.test.container.database;

import com.nortal.test.testcontainers.AbstractAuxiliaryContainer;
import com.nortal.test.testcontainers.configuration.TestableContainerJacocoProperties;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.springframework.stereotype.Component;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Objects;

import static org.testcontainers.containers.PostgreSQLContainer.POSTGRESQL_PORT;

@SuppressWarnings("resource")
@Component
@RequiredArgsConstructor
public class PostgresContextualContainer extends AbstractAuxiliaryContainer<PostgresContextualContainer.XRoadTestPostgreSQLContainer> {
    private static final String POSTGRES_IMAGE_NAME = "postgres:14";

    private final TestableContainerJacocoProperties jacocoProperties;

    public static class XRoadTestPostgreSQLContainer extends PostgreSQLContainer<XRoadTestPostgreSQLContainer> {

        public XRoadTestPostgreSQLContainer() {
            super(POSTGRES_IMAGE_NAME);
        }
    }

    @NotNull
    @Override
    public String getConfigurationKey() {
        return "xrd-db";
    }

    @NotNull
    @Override
    @SuppressWarnings("checkstyle:magicnumber")
    public XRoadTestPostgreSQLContainer configure() {
        return new XRoadTestPostgreSQLContainer()
                .withCreateContainerCmdModifier(cmd -> Objects.requireNonNull(cmd.getHostConfig()).withMemory(512 * 1024 * 1024L))
                .withDatabaseName("op-monitor")
                .withUsername("xrd")
                .withPassword("secret")
                .withNetworkAliases("xrd-db");

    }

    public String getExternalJdbcUrl() {
        return createDatabaseUrl(jacocoProperties.getHost(), getTestContainer().getMappedPort(POSTGRESQL_PORT));
    }

    public String getJdbcUrl() {
        return createDatabaseUrl("xrd-db", POSTGRESQL_PORT);
    }

    private String createDatabaseUrl(String host, int port) {
        Assertions.assertTrue(isRunning());
        return String.format("jdbc:postgresql://%s:%d/%s",
                host,
                port,
                getTestContainer().getDatabaseName());
    }
}
