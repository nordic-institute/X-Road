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

import java.nio.file.Path;

/**
 * Host-level access to a server of the environment: command execution, file transfer and service control.
 * It serves the bare op-monitor host ({@code opmon}), which has no admin API or database access to go
 * through, and the Security Server hosts that a scenario probes from the inside.
 *
 * <p>Implemented only by the LXD adapter: the Compose stack runs op-monitor as a container of its own and
 * the k8s topology has no bare op-monitor host, so a scenario needing this must self-skip there.
 */
public interface OpMonHostOps {

    /**
     * Runs a command on the host.
     */
    HostCommandResult exec(String env, String... command);

    /**
     * Runs {@code systemctl} with the given arguments on the host.
     */
    HostCommandResult systemctl(String env, String... arguments);

    /**
     * Copies a file from the host to the local machine.
     */
    void pullFile(String env, String remotePath, Path localPath);

    /**
     * Copies a local file to the host.
     */
    void pushFile(String env, Path localPath, String remotePath);

    /**
     * The outcome of a command run on a host.
     *
     * @param stdout   standard output, trimmed
     * @param stderr   standard error, trimmed
     * @param exitCode the exit code of the command
     */
    record HostCommandResult(String stdout, String stderr, int exitCode) {
    }
}
