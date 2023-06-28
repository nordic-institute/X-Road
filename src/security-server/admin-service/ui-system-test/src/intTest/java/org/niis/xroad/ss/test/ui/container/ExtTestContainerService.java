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
package org.niis.xroad.ss.test.ui.container;

import com.nortal.test.testcontainers.TestContainerNetworkProvider;
import com.nortal.test.testcontainers.TestContainerService;
import com.nortal.test.testcontainers.configuration.TestableContainerProperties;
import com.nortal.test.testcontainers.configurator.TestContainerConfigurator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

@Primary
@Service
@Slf4j
public class ExtTestContainerService extends TestContainerService {
    @Value("${test-automation.custom.security-server-url-override:#{null}}")
    private String centralServerUrlOverride;

    public ExtTestContainerService(@NotNull final TestContainerNetworkProvider testContainerNetworkProvider,
                                   @NotNull final TestableContainerProperties testableContainerProperties,
                                   @NotNull final TestContainerConfigurator testContainerConfigurator,
                                   @NotNull final List<TestContainerConfigurator.TestContainerInitListener> listeners) {
        super(testContainerNetworkProvider, testableContainerProperties, testContainerConfigurator, listeners);
    }

    @Override
    public void initialize() {
        if (isUrlOverridden()) {
            log.warn("Target host url override is set. Container initialization is disabled.");
        } else {
            super.initialize();

        }
    }

    @Override
    public @NotNull String getHost() {
        if (isUrlOverridden()) {
            final String[] urlParts = getUrlParts();
            return urlParts[1].substring(2);
        } else {
            return super.getHost();
        }
    }

    @Override
    public int getPort() {
        if (isUrlOverridden()) {
            final String[] urlParts = getUrlParts();
            return Integer.parseInt(urlParts[2]);
        } else {
            return super.getPort();
        }
    }

    private String[] getUrlParts() {
        return StringUtils.split(centralServerUrlOverride, ':');
    }

    private boolean isUrlOverridden() {
        return centralServerUrlOverride != null;
    }
}
