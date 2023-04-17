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
package org.niis.xroad.cs.test.ui.container;

import com.nortal.test.testcontainers.AbstractTestableContainerSetup;
import com.nortal.test.testcontainers.images.builder.ImageFromDockerfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.niis.xroad.cs.test.ui.TargetHostUrlProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.testcontainers.images.builder.dockerfile.DockerfileBuilder;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContainerSetup extends AbstractTestableContainerSetup {
    private static final String VERIFY_EXTERNAL_CONFIGURATION_PATH = "usr/share/xroad/scripts/verify_external_configuration.sh";
    private static final String VERIFY_EXTERNAL_CONFIGURATION_FILE_PATH =
            "src/intTest/resources/container-files/" + VERIFY_EXTERNAL_CONFIGURATION_PATH;
    private final TargetHostUrlProvider targetHostUrlProvider;

    @Value("${test-automation.custom.package-repo}")
    private String packageRepo;
    @Value("${test-automation.custom.package-repo-key}")
    private String packageRepoKey;


    @NotNull
    @Override
    public String applicationName() {
        return "cs-e2e";
    }

    @NotNull
    @Override
    public String maxMemory() {
        return "768m";
    }

    @Override
    public int[] getTargetContainerExposedPorts() {
        return super.getTargetContainerExposedPorts();
    }

    @Override
    public void additionalBuilderConfiguration(@NotNull DockerfileBuilder dockerfileBuilder) {
        //do nothing
    }

    @NotNull
    @Override
    public List<String> additionalCommandParts() {
        return Collections.emptyList();
    }

    @Override
    public void additionalImageFromDockerfileConfiguration(@NotNull ImageFromDockerfile imageFromDockerfile) {
        //do nothing
    }

    @NotNull
    @Override
    protected ImageFromDockerfile build() {
        Path csDockerRoot = Paths.get("../../../../Docker/centralserver/");
        Path dockerfilePath = csDockerRoot.resolve("Dockerfile");

        return new ImageFromDockerfile("cs-system-test", true)
                .withBuildArg("DIST", "jammy")
                .withBuildArg("REPO", packageRepo)
                .withBuildArg("REPO_KEY", packageRepoKey)

                .withFileFromPath("Dockerfile", dockerfilePath)
                .withFileFromFile(".", csDockerRoot.resolve("build/").toFile())
                .withFileFromPath(VERIFY_EXTERNAL_CONFIGURATION_PATH, Paths.get(VERIFY_EXTERNAL_CONFIGURATION_FILE_PATH));
    }

    @Override
    public void initialize() {
        if (targetHostUrlProvider.isUrlOverridden()) {
            log.warn("Target host url override is set. Container initialization is disabled.");
        } else {
            super.initialize();

        }
    }

    @Override
    public void onContainerStartupInitiated() {
        //do nothing
    }
}
