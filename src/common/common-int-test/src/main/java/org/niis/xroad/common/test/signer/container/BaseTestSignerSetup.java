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
package org.niis.xroad.common.test.signer.container;

import com.nortal.test.testcontainers.configuration.TestableContainerProperties;
import com.nortal.test.testcontainers.configurator.TestContainerConfigurator;
import com.nortal.test.testcontainers.images.builder.ImageFromDockerfile;
import com.nortal.test.testcontainers.images.builder.ReusableImageFromDockerfile;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@SuppressWarnings("checkstyle:MagicNumber")
public abstract class BaseTestSignerSetup {

    static {
        //This is to set docker api version in testcontainers. By default it uses 1.32, which does not support platform setting.
        System.setProperty("api.version", "1.41");
    }

    public TestContainerConfigurator testContainerConfigurator(
            TestableContainerProperties testableContainerProperties) {
        return new TestContainerConfigurator() {
            @NotNull
            @Override
            public ImageFromDockerfile imageDefinition() {
                var appJarPath = Paths.get("../signer/build/libs/signer-1.0.jar");
                var hwTokenJarPath = Paths.get("../addons/hwtoken/build/libs/hwtoken-1.0.jar");
                log.info("Will use {} jar for container creation", appJarPath);

                File filesToAdd = Paths.get("build/resources/intTest/signer-container-files/").toFile();

                return new ReusableImageFromDockerfile("signer-int-test",
                        !testableContainerProperties.getReuseBetweenRuns(),
                        testableContainerProperties.getReuseBetweenRuns())
                        .withFileFromFile(".", filesToAdd)
                        .withFileFromPath("files/app.jar", appJarPath)
                        .withFileFromPath("files/hwtoken.jar", hwTokenJarPath);
            }

            @NotNull
            @Override
            public Map<String, String> environmentalVariables() {
                return new HashMap<>();
            }

            @NotNull
            @Override
            public List<Integer> exposedPorts() {
                return List.of(5558, 5560);
            }
        };
    }

    public TestContainerConfigurator.TestContainerInitListener testContainerInitListener(boolean enableHwModule) {
        return new TestContainerConfigurator.TestContainerInitListener() {

            @Override
            @SuppressWarnings("squid:S2068")
            public void beforeStart(@NotNull GenericContainer<?> genericContainer) {
                var modulemanager = enableHwModule
                        ? "-Dxroad.signer.moduleManagerImpl=ee.ria.xroad.signer.tokenmanager.module.HardwareModuleManagerImpl"
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
                                modulemanager,
                                "-cp",
                                "/root/lib/hwtoken.jar:/root/app.jar",
                                "ee.ria.xroad.signer.SignerMain");

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
