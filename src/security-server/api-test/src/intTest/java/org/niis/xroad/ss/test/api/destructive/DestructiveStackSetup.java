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
import org.niis.xroad.ss.test.api.SsApiTestContainerSetup;
import org.niis.xroad.test.apitest.core.config.ApiTestCoreProperties;

/**
 * Disposable Security Server stack for the destructive-lifecycle lane. Uses a distinct Docker Compose
 * project name ({@code ss-destructive-}) so it runs concurrently with the warm-substrate stack
 * ({@code ss-api-}) without port or state collisions.
 *
 * <p>Inherits the full stack recipe (compose files, exposed services, log consumers, DSP bootstrap)
 * from {@link SsApiTestContainerSetup}. Tests on this lane may stop, restart, or otherwise mutate
 * services without affecting the warm substrate.
 */
@Slf4j
class DestructiveStackSetup extends SsApiTestContainerSetup {

    DestructiveStackSetup(ApiTestCoreProperties coreProperties) {
        super(coreProperties);
    }

    @Override
    protected String composeProjectName() {
        return "ss-destructive-";
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
