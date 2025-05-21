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
    private static final String PKCS11_WRAPPER_FILENAME = "libpkcs11wrapper.so";
    private static final String PKCS11_WRAPPER_DIR = "../../../libs/pkcs11wrapper/%s/%s";
    private static final String SIGNER_RUNTIME_PATH = "../signer-application/build/quarkus-app";

    @Bean
    public TestContainerConfigurator testContainerConfigurator(TestableContainerProperties testableContainerProperties) {
        return new TestContainerConfigurator() {

            @Override
            public @NotNull ImageFromDockerfile imageDefinition() {
                var appJarPath = Paths.get(SIGNER_RUNTIME_PATH);

                log.info("Will use {} jar for container creation", appJarPath);

                File filesToAdd = Paths.get("build/resources/intTest/signer-container-files/").toFile();

                return new ReusableImageFromDockerfile("signer-int-test",
                        !testableContainerProperties.getReuseBetweenRuns(),
                        testableContainerProperties.getReuseBetweenRuns())
                        .withFileFromFile(".", filesToAdd)
                        .withFileFromPath("files/lib/%s".formatted(PKCS11_WRAPPER_FILENAME), getPkcsWrapperPath())
                        .withFileFromPath("files/app/", appJarPath);
            }

            @Override
            public @NotNull Map<String, String> environmentalVariables() {
                return new HashMap<>();
            }


            @Override
            @SuppressWarnings("checkstyle:MagicNumber")
            public @NotNull List<Integer> exposedPorts() {
                return List.of(5560);
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
                genericContainer
                        .waitingFor(Wait.forLogMessage(".*signer .* started in.*", 1));
                genericContainer
                        .withCommand("java",
                                "-Xmx50m",
                                "-XX:MaxMetaspaceSize=70m",
                                "-Djava.library.path=/root/lib/",
                                "-Dxroad.signer.addon.hwtoken.enabled=true",
                                "-Dxroad.common.rpc.use-tls=false",
                                "-jar",
                                "/root/app/quarkus-run.jar");

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
