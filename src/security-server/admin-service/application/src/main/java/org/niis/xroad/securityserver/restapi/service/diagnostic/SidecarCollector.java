/*
 * The MIT License
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.securityserver.restapi.service.diagnostic;

import ee.ria.xroad.common.FilePaths;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Component
@RequiredArgsConstructor
@Order(DiagnosticCollector.ORDER_GROUP4)
public class SidecarCollector implements DiagnosticCollector<SidecarCollector.Containerized> {

    private static final String DOCKER_ENV_PATH = "/.dockerenv";
    private static final String MOUNT_INFO_PATH = "/proc/self/mountinfo";
    private static final String KUBERNETES_SECRETS_PATH = "/var/run/secrets/kubernetes.io";
    private static final String KUBERNETES_SERVICE_HOST_KEY = "KUBERNETES_SERVICE_HOST";

    @Override
    public String name() {
        return "Runs in container";
    }

    @Override
    public Containerized collect() {

        boolean containerized = dockerEnvExists() || inKubernetes() || checkMountInfo();

        return new Containerized(containerized, Files.exists(FilePaths.NODE_INI_PATH));
    }

    private static boolean dockerEnvExists() {
        return Files.exists(Paths.get(DOCKER_ENV_PATH));
    }

    private static boolean checkMountInfo() {
        var mountInfo = Paths.get(MOUNT_INFO_PATH);
        if (!Files.exists(mountInfo)) {
            return false;
        }
        try (var lines = Files.lines(mountInfo)) {

            return lines
                    .anyMatch(line -> line.contains("/docker/")
                            || line.contains("/lxd/"));

        } catch (IOException e) {
            throw XrdRuntimeException.systemException(e);
        }
    }

    private static boolean inKubernetes() {
        if (System.getenv(KUBERNETES_SERVICE_HOST_KEY) != null) {
            return true;
        }

        return Files.exists(Paths.get(KUBERNETES_SECRETS_PATH));
    }

    public record Containerized(boolean containerized, boolean asNode) {

    }
}
