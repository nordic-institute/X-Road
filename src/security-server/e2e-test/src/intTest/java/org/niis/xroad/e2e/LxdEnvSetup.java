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

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * LXD-based implementation of the e2e environment.
 * Assumes the environment is already provisioned and bootstrapped by Ansible.
 * Bring-up and teardown are no-ops.
 */
@Slf4j
@RequiredArgsConstructor
public class LxdEnvSetup implements E2eEnvironment, MessagelogDbOps {

    private static final int PROBE_TIMEOUT_MS = 5000;
    private static final String MESSAGELOG_SEARCH_PATH = "--search_path=messagelog,public";

    private final LxdEnvProperties lxdProperties;

    @Override
    public ContainerMapping getContainerMapping(String env, String service, int port) {
        String host = resolveHost(env);
        return new ContainerMapping(host, port);
    }

    @Override
    public boolean isInitialized() {
        return probePort(lxdProperties.ss0Host(), lxdProperties.proxyPort())
                && probePort(lxdProperties.ss1Host(), lxdProperties.proxyPort());
    }

    @Override
    public String peerControlPlaneHost(String env) {
        return "xrd-" + env + ".lxd";
    }

    @Override
    public String participantContextId(String env) {
        return resolveHost(env);
    }

    @Override
    public String participantDid(String env) {
        return "did:web:%s%%3A7183".formatted(resolveHost(env));
    }

    @Override
    public String securityServerAddress(String env) {
        return resolveHost(env);
    }

    @Override
    @SneakyThrows
    public String execMessagelogSql(String env, String sql) {
        var container = "xrd-" + env;
        var process = new ProcessBuilder(
                lxdProperties.lxcCommand(), "exec", container, "--",
                "sudo", "-u", "postgres", "PGOPTIONS=" + MESSAGELOG_SEARCH_PATH,
                "psql", "-d", "messagelog", "-tAX", "-c", sql)
                .start();

        // Stdout and stderr are drained concurrently to avoid deadlocking on a full pipe buffer:
        // lxc exec emits sudoers noise on stderr on every call, which must not be mistaken for failure.
        var stdoutFuture = CompletableFuture.supplyAsync(() -> readAll(process.getInputStream()));
        var stderrFuture = CompletableFuture.supplyAsync(() -> readAll(process.getErrorStream()));
        var stdout = stdoutFuture.get();
        var stderr = stderrFuture.get();
        var exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new IllegalStateException("psql query on %s failed (exit %d): %s".formatted(env, exitCode, stderr));
        }
        return stdout.trim();
    }

    @SneakyThrows
    private static String readAll(InputStream inputStream) {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
    }

    private String resolveHost(String env) {
        return switch (env) {
            case "ss0" -> lxdProperties.ss0Host();
            case "ss1" -> lxdProperties.ss1Host();
            case "aux" -> lxdProperties.csHost();
            default -> throw new IllegalArgumentException("Unknown LXD environment: " + env);
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
}
