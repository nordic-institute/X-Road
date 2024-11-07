/*
 * The MIT License
 * <p>
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
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

package org.niis.xroad.common.test.container;

import com.nortal.test.testcontainers.AbstractAuxiliaryContainer;
import com.nortal.test.testcontainers.configuration.ContainerProperties;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SystemUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Objects;


@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(value = "test-automation.containers.context-containers.mail-server.enabled", havingValue = "true")
@SuppressWarnings("checkstyle:MagicNumber")
public class TestMailServerAuxiliaryContainer extends AbstractAuxiliaryContainer<TestMailServerAuxiliaryContainer.TestMailServerContainer> {
    private static final String NETWORK_ALIAS = "testmailserver";

    private final ContainerProperties testableContainerProperties;

    public static class TestMailServerContainer extends GenericContainer<TestMailServerContainer> {
        public TestMailServerContainer(@NonNull String image) {
            super(image);
        }
    }

    @NotNull
    @Override
    public TestMailServerContainer configure() {
        var dataDirPath = Paths.get("build/mail-server-data/");
        var dataDir = dataDirPath.toFile();
        dataDir.mkdirs();

        if (SystemUtils.IS_OS_UNIX) {
            try {
                Files.setPosixFilePermissions(dataDirPath, PosixFilePermissions.fromString("rwxrwxrwx"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        Logger logger = LoggerFactory.getLogger(getConfigurationKey());
        Slf4jLogConsumer logConsumer = new Slf4jLogConsumer(logger).withSeparateOutputStreams();
        return getTestMailServerContainer(dataDir, logConsumer);
    }

    private TestMailServerContainer getTestMailServerContainer(File dataDir, Slf4jLogConsumer logConsumer) {
        try (TestMailServerContainer testMailServerContainer = new TestMailServerContainer("axllent/mailpit")
                .withExposedPorts(587, 8025)
                .withFileSystemBind(dataDir.getAbsolutePath(), "/data", BindMode.READ_WRITE)
                .withCopyToContainer(MountableFile.forClasspathResource("META-INF/mail-server-container/"), "/tls")
                .withEnv("MP_DATABASE", "/data/mailpit.db")
                .withEnv("MP_SMTP_BIND_ADDR", "0.0.0.0:587")
                //.withEnv("MP_SMTP_REQUIRE_STARTTLS", "1")
                .withEnv("MP_SMTP_REQUIRE_TLS", "1")
                .withEnv("MP_SMTP_TLS_KEY", "/tls/testmailserver_key.pem")
                .withEnv("MP_SMTP_TLS_CERT", "/tls/testmailserver_cert.pem")
                .withEnv("MP_SMTP_AUTH_FILE", "/tls/password_file")
                .withNetworkAliases(NETWORK_ALIAS)
                .withReuse(testableContainerProperties.getContextContainers().get(getConfigurationKey()).getReuseBetweenRuns())
                .withLogConsumer(logConsumer)
                .withCreateContainerCmdModifier(cmd -> Objects.requireNonNull(cmd.getHostConfig()).withMemory(192 * 1024 * 1024L))) {
            return testMailServerContainer;
        }
    }

    @NotNull
    @Override
    public String getConfigurationKey() {
        return "mail-server";
    }

}
