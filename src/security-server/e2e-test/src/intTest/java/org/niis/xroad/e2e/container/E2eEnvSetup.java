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
package org.niis.xroad.e2e.container;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.test.apitest.core.config.ApiTestCoreProperties;
import org.niis.xroad.test.apitest.core.container.BaseComposeSetup;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Container;

import java.time.Duration;
import java.util.Set;

import static org.awaitility.Awaitility.await;

/**
 * Facade over the e2e topology's three independent compose stacks: aux (Central Server + hurl setup),
 * ss0 and ss1 (Security Servers). Extends {@link BaseComposeSetup} only so it satisfies the type the
 * api-test-core substrate's {@code AbstractApiStackSessionListener}/{@code ApiStackExtension} lifecycle
 * expects; its single-stack accessors are not meaningful for a three-stack topology and are overridden
 * to fail loudly instead of silently touching a never-populated {@code env} field. Test code must use
 * the env-qualified overloads below (e.g. {@link #getContainerMapping(String, String, int)}).
 */
@Slf4j
public class E2eEnvSetup extends BaseComposeSetup {

    private static final String DS_HTTPS_KEYSTORE_VOLUME = "e2e-ds-https-keystore";
    private static final Duration GLOBALCONF_PROPAGATION_GRACE_PERIOD = Duration.ofSeconds(20);

    private AuxStackSetup aux;
    private SsStackSetup ss0;
    private SsStackSetup ss1;

    public E2eEnvSetup(ApiTestCoreProperties coreProperties) {
        super(coreProperties);
    }

    @Override
    public void start() {
        ensureDsHttpsKeystoreVolume();

        aux = new AuxStackSetup(coreProperties);
        aux.start();

        ss0 = new SsStackSetup(coreProperties, "ss0",
                Set.of(SsStackSetup.Feature.BATCH_SIGNATURES, SsStackSetup.Feature.SOFTTOKEN_SIGNER, SsStackSetup.Feature.OP_MONITOR));
        ss0.start();

        ss1 = new SsStackSetup(coreProperties, "ss1",
                Set.of(SsStackSetup.Feature.HSM, SsStackSetup.Feature.MESSAGE_LOG_ENCRYPTION));
        ss1.start();

        aux.waitForHurlToFinish();
        ss0.awaitProxyReadiness();
        ss1.awaitProxyReadiness();

        log.info("Waiting grace period of {} for global configuration to propagate..", GLOBALCONF_PROPAGATION_GRACE_PERIOD);
        await().pollDelay(GLOBALCONF_PROPAGATION_GRACE_PERIOD)
                .timeout(GLOBALCONF_PROPAGATION_GRACE_PERIOD.plusMinutes(1))
                .until(() -> true);
    }

    @Override
    public void stop() {
        if (ss1 != null) {
            ss1.stop();
        }
        if (ss0 != null) {
            ss0.stop();
        }
        if (aux != null) {
            aux.stop();
        }
    }

    public boolean isAuxHurlRunning() {
        return aux.isHurlRunning();
    }

    public ContainerMapping getContainerMapping(String envName, String service, int originalPort) {
        return mapEnvironment(envName).getContainerMapping(service, originalPort);
    }

    /**
     * Named {@code execInEnvContainer} rather than an {@code execInContainer} overload: both this and the
     * single-stack {@link #execInContainer(String, String...)} override take a trailing {@code String...},
     * so a same-named overload would be arity-ambiguous at every call site with more than one command token.
     */
    public Container.ExecResult execInEnvContainer(String envName, String container, String... command) {
        return mapEnvironment(envName).execInContainer(container, command);
    }

    public void copyFileFromContainer(String envName, String container, String containerPath, String localPath) {
        mapEnvironment(envName).copyFileFromContainer(container, containerPath, localPath);
    }

    @SneakyThrows
    public String execMessagelogSql(String envName, String sql) {
        var result = execInEnvContainer(envName, SsStackSetup.DB_MESSAGELOG,
                "psql", "-U", "postgres", "-d", "messagelog", "-tAX", "-c", sql);
        if (result.getExitCode() != 0) {
            throw new IllegalStateException("psql query on %s failed: %s".formatted(envName, result.getStderr()));
        }
        return result.getStdout().trim();
    }

    private BaseComposeSetup mapEnvironment(String name) {
        return switch (name) {
            case "ss0" -> ss0;
            case "ss1" -> ss1;
            case "aux" -> aux;
            default -> throw new IllegalArgumentException("Unknown environment: " + name);
        };
    }

    private void ensureDsHttpsKeystoreVolume() {
        var dockerClient = DockerClientFactory.lazyClient();
        dockerClient.createVolumeCmd().withName(DS_HTTPS_KEYSTORE_VOLUME).exec();
        log.info("Ensured external docker volume {} exists", DS_HTTPS_KEYSTORE_VOLUME);
    }

    @Override
    protected String composeProjectName() {
        throw new UnsupportedOperationException(
                "E2eEnvSetup manages three independent compose stacks; see AuxStackSetup/SsStackSetup composeProjectName()");
    }

    @Override
    public ContainerMapping getContainerMapping(String service, int originalPort) {
        throw new UnsupportedOperationException("Use getContainerMapping(env, service, port); this facade manages three stacks");
    }

    @Override
    public Container.ExecResult execInContainer(String container, String... command) {
        throw new UnsupportedOperationException("Use execInEnvContainer(env, container, command); this facade manages three stacks");
    }

    @Override
    public void restartService(String containerName) {
        throw new UnsupportedOperationException("Not supported on the multi-stack facade");
    }

    @Override
    public void copyFilesToContainer(String containerName, String classpathResource, String targetPath) {
        throw new UnsupportedOperationException("Not supported on the multi-stack facade");
    }

    @Override
    public void copyFileFromContainer(String containerName, String containerPath, String localPath) {
        throw new UnsupportedOperationException("Use copyFileFromContainer(env, container, containerPath, localPath); "
                + "this facade manages three stacks");
    }
}
