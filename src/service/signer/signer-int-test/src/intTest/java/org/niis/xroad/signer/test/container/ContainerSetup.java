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
package org.niis.xroad.signer.test.container;

import com.nortal.test.testcontainers.configuration.TestableContainerProperties;
import com.nortal.test.testcontainers.configurator.TestContainerConfigurator;
import com.nortal.test.testcontainers.images.builder.ImageFromDockerfile;
import com.nortal.test.testcontainers.images.builder.ReusableImageFromDockerfile;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Configuration
public class ContainerSetup {
    private static final boolean HWTOKEN_ENABLED = true;
    private static final String PKCS11_WRAPPER_FILENAME = "libpkcs11wrapper.so";
    private static final String PKCS11_WRAPPER_DIR = "../../../libs/pkcs11wrapper/%s/%s";
    private static final String SIGNER_JAR_PATH = "../signer-application/build/libs/signer-1.0.jar";
    private static final String HWTOKEN_JAR_PATH = "../../../addons/hwtoken/build/libs/hwtoken-1.0.jar";

    @Bean
    public TestContainerConfigurator testContainerConfigurator(TestableContainerProperties testableContainerProperties) {
        return new TestContainerConfigurator() {

            @Override
            public @NotNull ImageFromDockerfile imageDefinition() {
                var appJarPath = Paths.get(SIGNER_JAR_PATH);
                var hwTokenJarPath = Paths.get(HWTOKEN_JAR_PATH);

                log.info("Will use {} jar for container creation", appJarPath);

                File filesToAdd = Paths.get("build/resources/intTest/signer-container-files/").toFile();

                return new ReusableImageFromDockerfile("signer-int-test",
                        !testableContainerProperties.getReuseBetweenRuns(),
                        testableContainerProperties.getReuseBetweenRuns())
                        .withFileFromFile(".", filesToAdd)
                        .withFileFromPath("files/lib/%s".formatted(PKCS11_WRAPPER_FILENAME), getPkcsWrapperPath())
                        .withFileFromPath("files/lib/hwtoken.jar", hwTokenJarPath)
                        .withFileFromPath("files/app.jar", appJarPath);
            }

            @Override
            public @NotNull Map<String, String> environmentalVariables() {
                return new HashMap<>();
            }


            @Override
            @SuppressWarnings("checkstyle:MagicNumber")
            public @NotNull List<Integer> exposedPorts() {
                return List.of(5558, 5560);
            }

            private Path getPkcsWrapperPath() {
                String archDir = switch (SystemUtils.OS_ARCH) {
                    case "x86_64", "amd64" -> "amd64";
                    case "aarch64", "arm64" -> "arm64";
                    default -> throw new IllegalStateException("Unsupported arch: " + SystemUtils.OS_ARCH);
                };
                return Paths.get(PKCS11_WRAPPER_DIR.formatted(archDir, PKCS11_WRAPPER_FILENAME));

            }
        };
    }

    @Bean
    public TestContainerConfigurator.TestContainerInitListener testContainerInitListener() {
        return new TestContainerConfigurator.TestContainerInitListener() {

            @Override
            @SuppressWarnings("squid:S2068")
            public void beforeStart(@NotNull GenericContainer<?> genericContainer) {
                var modulemanager = HWTOKEN_ENABLED
                        ? "-Dxroad.signer.moduleManagerImpl=org.niis.xroad.signer.core.tokenmanager.module.HardwareModuleManagerImpl"
                        : "";

                genericContainer
                        .waitingFor(Wait.forLogMessage(".*Signer has been initialized in.*", 1));
                genericContainer
                        .withCommand("java",
                                "-Xmx50m",
                                "-XX:MaxMetaspaceSize=70m",
                                "-Dlogback.configurationFile=/etc/xroad/signer/signer-logback.xml",
                                "-Dxroad.internal.passwordstore-provider=file",
                                "-Dxroad.common.grpc-internal-host=0.0.0.0",
                                "-Dxroad.common.grpc-internal-keystore=/etc/xroad/transport-keystore/grpc-internal-keystore.p12",
                                "-Dxroad.common.grpc-internal-keystore-password=111111",
                                "-Dxroad.common.grpc-internal-truststore=/etc/xroad/transport-keystore/grpc-internal-keystore.p12",
                                "-Dxroad.common.grpc-internal-truststore-password=111111",
                                "-Djava.library.path=/root/lib/",
                                modulemanager,
                                "-cp",
                                "/root/lib/hwtoken.jar:/root/app.jar",
                                "org.niis.xroad.signer.application.SignerMain");

                prepareSignerDirs();
            }

            @Override
            public void afterStart(@NotNull GenericContainer<?> genericContainer) {
                //do nothing
            }

            @SneakyThrows
            private void prepareSignerDirs() {
                deleteIfPresent("build/resources/intTest/signer-container-files/etc/xroad/signer/softtoken/");
                deleteIfPresent("build/container-passwordstore/");
            }

            @SneakyThrows
            private void deleteIfPresent(String path) {
                var dir = Paths.get(path);
                if (dir.toFile().exists()) {
                    log.info("Temporary test-signer sync dir {} found. Deleting..", dir);
                    FileUtils.cleanDirectory(dir.toFile());
                }
            }
        };
    }


}
