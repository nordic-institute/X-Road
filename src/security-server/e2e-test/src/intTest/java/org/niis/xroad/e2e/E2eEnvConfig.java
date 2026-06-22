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

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.test.framework.core.config.TestFrameworkConfigSource;
import org.niis.xroad.test.framework.core.config.TestFrameworkCoreProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires exactly one environment bean selected by {@code test-framework.env-mode}.
 *
 * <p>In {@code compose} mode the bean's runtime type is {@link ComposeEnvSetup}, which implements
 * both {@link E2eEnvironment} and {@link ComposeContainerOps}. Spring's injection resolves both
 * interface types from that single bean; {@code ObjectProvider<ComposeContainerOps>} finds it.
 *
 * <p>In {@code lxd} mode the runtime type is {@link LxdEnvSetup}, which does NOT implement
 * {@link ComposeContainerOps}. Spring finds no {@link ComposeContainerOps} bean;
 * {@code ObjectProvider<ComposeContainerOps>.getIfAvailable()} returns {@code null} safely.
 *
 * <p>Neither impl carries {@code @Component}, so component-scan never produces duplicates.
 */
@Slf4j
@Configuration
public class E2eEnvConfig {

    @Bean
    LxdEnvProperties lxdEnvProperties() {
        return TestFrameworkConfigSource.getInstance().buildConfigMapping(LxdEnvProperties.class);
    }

    /**
     * Returns the active environment implementation. The declared return type is
     * {@link E2eEnvironment}; Spring resolves autowiring by the actual runtime class,
     * so in compose mode it also satisfies {@link ComposeContainerOps} injection points.
     */
    @Bean
    E2eEnvironment e2eEnvironment(TestFrameworkCoreProperties coreProperties, LxdEnvProperties lxdEnvProperties) {
        var mode = coreProperties.envMode();
        log.info("e2e environment mode: {}", mode);
        return switch (mode) {
            case "compose" -> new ComposeEnvSetup(coreProperties);
            case "lxd" -> new LxdEnvSetup(lxdEnvProperties);
            default -> throw new IllegalArgumentException("Unknown env-mode: " + mode + " — expected 'compose' or 'lxd'");
        };
    }
}
