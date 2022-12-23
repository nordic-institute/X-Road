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
package org.niis.xroad.cs.test.container;

import com.nortal.test.testcontainers.AbstractTestableSpringBootContainerSetup;
import com.nortal.test.testcontainers.images.builder.ImageFromDockerfile;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.testcontainers.images.builder.dockerfile.DockerfileBuilder;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ContainerSetup extends AbstractTestableSpringBootContainerSetup {

    @NotNull
    @Override
    public String applicationName() {
        return "cs-management-service";
    }

    @NotNull
    @Override
    public String maxMemory() {
        return "512m";
    }

    @Override
    public int[] getTargetContainerExposedPorts() {
        return super.getTargetContainerExposedPorts();
    }

    @Override
    public void additionalBuilderConfiguration(@NotNull DockerfileBuilder dockerfileBuilder) {
        dockerfileBuilder.copy(".",".");
//        dockerfileBuilder.copy("/etc/xroad/conf.d/centralserver-admin-service-logback.xml",
//                "/etc/xroad/conf.d/centralserver-admin-service-logback.xml");
    }

    @NotNull
    @Override
    public List<String> additionalCommandParts() {
        return Collections.emptyList();
    }

    @Override
    public void additionalImageFromDockerfileConfiguration(@NotNull ImageFromDockerfile imageFromDockerfile) {
//        Path csDockerRoot = Paths.get("../container-files/");
        imageFromDockerfile.withFileFromFile(".", Paths.get("src/intTest/resources/container-files/").toFile());
        /*imageFromDockerfile.withFileFromClasspath(
                "/etc/xroad/conf.d/centralserver-admin-service-logback.xml",
                "container-files/etc/xroad/conf.d/centralserver-admin-service-logback.xml");*/
    }

    @Override
    public void onContainerStartupInitiated() {
        //do nothing
    }
}
