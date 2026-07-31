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
package org.niis.xroad.e2e;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.test.apitest.core.config.ApiTestCoreProperties;
import org.niis.xroad.test.apitest.core.container.BaseComposeSetup;
import org.testcontainers.containers.Container;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Kubernetes-based implementation of the e2e environment (tracer-bullet slice: a single,
 * minimally-provisioned Security Server). Assumes the environment is already provisioned by
 * {@code core/scripts/env-k8s} and reachable via its {@code kubectl port-forward} listeners, so
 * bring-up and teardown are no-ops. Extends {@link BaseComposeSetup} only to satisfy the
 * api-test-core session-listener/extension lifecycle; the inherited compose accessors are
 * overridden to fail loudly — test code must go through {@link E2eEnvironment}.
 */
@Slf4j
public class K8sEnvSetup extends BaseComposeSetup implements E2eEnvironment {

    private static final int PROBE_TIMEOUT_MS = 5000;

    private final K8sEnvProperties k8sProperties;

    public K8sEnvSetup(K8sEnvProperties k8sProperties, ApiTestCoreProperties coreProperties) {
        super(coreProperties);
        this.k8sProperties = k8sProperties;
    }

    @Override
    public void start() {
        log.info("Using pre-provisioned k8s environment (ss0={}:{}); bring-up is externally managed by "
                + "core/scripts/env-k8s", k8sProperties.ss0Host(), k8sProperties.proxyPort());
    }

    @Override
    public void stop() {
        log.info("Leaving k8s environment running; teardown is externally managed by core/scripts/env-k8s");
    }

    @Override
    public ContainerMapping getContainerMapping(String env, String service, int port) {
        return new ContainerMapping(resolveHost(env), port);
    }

    @Override
    public boolean isInitialized() {
        return probePort(k8sProperties.ss0Host(), k8sProperties.proxyPort());
    }

    @Override
    public String securityServerAddress(String env) {
        return resolveHost(env);
    }

    private String resolveHost(String env) {
        return switch (env) {
            case "ss0" -> k8sProperties.ss0Host();
            default -> throw new IllegalArgumentException("Unknown k8s environment: " + env);
        };
    }

    private boolean probePort(String host, int port) {
        try (var socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), PROBE_TIMEOUT_MS);
            log.debug("Probe {}:{} reachable", host, port);
            return true;
        } catch (IOException e) {
            log.warn("Probe {}:{} unreachable: {}", host, port, e.getMessage());
            return false;
        }
    }

    @Override
    protected String composeProjectName() {
        throw new UnsupportedOperationException("K8sEnvSetup does not manage a compose stack");
    }

    @Override
    public ContainerMapping getContainerMapping(String service, int originalPort) {
        throw new UnsupportedOperationException("Use getContainerMapping(env, service, port); there is no compose stack");
    }

    @Override
    public Container.ExecResult execInContainer(String container, String... command) {
        throw new UnsupportedOperationException("Not supported on the k8s adapter");
    }

    @Override
    public void restartService(String containerName) {
        throw new UnsupportedOperationException("Not supported on the k8s adapter");
    }

    @Override
    public void copyFilesToContainer(String containerName, String classpathResource, String targetPath) {
        throw new UnsupportedOperationException("Not supported on the k8s adapter");
    }

    @Override
    public void copyFileFromContainer(String containerName, String containerPath, String localPath) {
        throw new UnsupportedOperationException("Not supported on the k8s adapter");
    }
}
