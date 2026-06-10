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
package org.niis.xroad.ss.test.api.destructive;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.test.apitest.core.config.ApiTestCoreProperties;
import org.niis.xroad.test.apitest.core.container.BaseComposeSetup;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.utility.MountableFile;

import java.io.File;

import static org.testcontainers.containers.wait.strategy.Wait.forListeningPort;

/**
 * Disposable Security Server stack for the destructive-lifecycle lane. Uses a distinct Docker Compose
 * project name ({@code ss-destructive-}) so it runs concurrently with the warm-substrate stack
 * ({@code ss-api-}) without port or state collisions. No browser service is included.
 *
 * <p>The stack is booted once per Gradle test task invocation and torn down after the run.
 * Tests on this lane may stop, restart, or otherwise mutate services without affecting the warm substrate.
 */
@Slf4j
@SuppressWarnings("checkstyle:magicnumber")
class DestructiveStackSetup extends BaseComposeSetup {

    static final String SIGNER = "signer";
    static final String CONFIGURATION_CLIENT = "configuration-client";
    static final String PROXY = "proxy";
    static final String MONITOR = "monitor";
    static final String AUXILIARY_SERVICE = "auxiliary-service";
    static final String TESTCA = "testca";
    static final String DB_SERVERCONF = "db-serverconf";
    static final String DB_MESSAGELOG = "db-messagelog";
    static final String OP_MONITOR = "op-monitor";
    static final String NGINX = "nginx";
    static final String OPENBAO = "openbao";
    static final String UI = "ui";

    static final int PROXY_HEALTHCHECK_PORT = 5558;

    private static final int PROXY_HTTP_PORT = 8080;
    private static final int QUARKUS_HEALTH_PORT = 4099;
    private static final int DB_PORT = 5432;
    private static final int TEST_CA_PORT = 8888;
    static final int UI_PORT = 4000;

    private static final String COMPOSE_SS_FILE = "compose.main.yaml";
    private static final String COMPOSE_API_FILE = "compose.api.yaml";

    DestructiveStackSetup(ApiTestCoreProperties coreProperties) {
        super(coreProperties);
    }

    @Override
    public ComposeContainer initEnv() {
        return new ComposeContainer("ss-destructive-",
                new File(coreProperties.resourceDir() + COMPOSE_SS_FILE),
                new File(coreProperties.resourceDir() + COMPOSE_API_FILE))
                .withExposedService(PROXY, PROXY_HTTP_PORT, forListeningPort())
                .withExposedService(PROXY, PROXY_HEALTHCHECK_PORT, forListeningPort())
                .withExposedService(SIGNER, QUARKUS_HEALTH_PORT, forListeningPort())
                .withExposedService(CONFIGURATION_CLIENT, QUARKUS_HEALTH_PORT, forListeningPort())
                .withExposedService(OP_MONITOR, QUARKUS_HEALTH_PORT, forListeningPort())
                .withExposedService(AUXILIARY_SERVICE, QUARKUS_HEALTH_PORT, forListeningPort())
                .withExposedService(UI, UI_PORT, forListeningPort())
                .withExposedService(DB_SERVERCONF, DB_PORT, forListeningPort())
                .withExposedService(DB_MESSAGELOG, DB_PORT, forListeningPort())
                .withExposedService(TESTCA, TEST_CA_PORT, forListeningPort())
                .withLogConsumer(UI, createLogConsumer(UI))
                .withLogConsumer(PROXY, createLogConsumer(PROXY))
                .withLogConsumer(SIGNER, createLogConsumer(SIGNER))
                .withLogConsumer(CONFIGURATION_CLIENT, createLogConsumer(CONFIGURATION_CLIENT))
                .withLogConsumer(MONITOR, createLogConsumer(MONITOR))
                .withLogConsumer(AUXILIARY_SERVICE, createLogConsumer(AUXILIARY_SERVICE))
                .withLogConsumer(OP_MONITOR, createLogConsumer(OP_MONITOR))
                .withLogConsumer(OPENBAO, createLogConsumer(OPENBAO))
                .withLogConsumer(NGINX, createLogConsumer(NGINX))
                .withLogConsumer(TESTCA, createLogConsumer(TESTCA));
    }

    @Override
    protected void onPostStart() {
        var nginxFiles = MountableFile.forClasspathResource("nginx-container-files/var/lib");
        env.getContainerByServiceName(NGINX).orElseThrow()
                .copyFileToContainer(nginxFiles, "/var/lib");
        execInContainer(AUXILIARY_SERVICE, "/etc/xroad/backup-keys/init_backup_encryption.sh");
    }

    /**
     * Stops the named service container and waits for it to reach the stopped state.
     * The container is not removed — it can be restarted via {@link #startService(String)}.
     */
    void stopService(String serviceName) {
        var container = env.getContainerByServiceName(serviceName).orElseThrow(
                () -> new IllegalStateException("Container not found: " + serviceName));
        var containerId = container.getContainerId();
        log.info("Stopping service container: {} ({})", serviceName, containerId);
        container.getDockerClient().stopContainerCmd(containerId).exec();
        log.info("Service container stopped: {}", serviceName);
    }

    /**
     * Starts a previously stopped service container.
     */
    void startService(String serviceName) {
        var container = env.getContainerByServiceName(serviceName).orElseThrow(
                () -> new IllegalStateException("Container not found: " + serviceName));
        var containerId = container.getContainerId();
        log.info("Starting service container: {} ({})", serviceName, containerId);
        container.getDockerClient().startContainerCmd(containerId).exec();
        log.info("Service container started: {}", serviceName);
    }

}
