/*
 * The MIT License
 *
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
package org.niis.xroad.ss.test.api.destructive;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.niis.xroad.ss.test.api.Port;
import org.niis.xroad.ss.test.api.seeding.SsBaselineSeeder;
import org.niis.xroad.test.apitest.core.config.ApiTestConfigSource;

/**
 * JUnit5 extension that boots the disposable Security Server stack for the destructive-lifecycle lane
 * once per JVM (per Gradle task invocation) and tears it down after all tests complete.
 *
 * <p>Distinct from {@link org.niis.xroad.ss.test.api.ApiStackExtension}: it uses the
 * {@link DestructiveStackSetup} with a different Docker Compose project name so the disposable stack
 * runs concurrently with the warm-substrate stack without collision.
 *
 * <p>Tests obtain the stack by declaring a {@link DestructiveStackSetup} parameter.
 */
@Slf4j
public class DestructiveStackExtension implements ParameterResolver {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(DestructiveStackExtension.class);
    private static final String KEY = "ss-destructive-stack";

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType() == DestructiveStackSetup.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return extensionContext.getRoot().getStore(NAMESPACE)
                .getOrComputeIfAbsent(KEY, key -> new StackResource(), StackResource.class)
                .setup();
    }

    private static final class StackResource implements ExtensionContext.Store.CloseableResource {
        private final DestructiveStackSetup setup;

        private StackResource() {
            var properties = ApiTestConfigSource.getInstance().getCoreProperties();
            this.setup = new DestructiveStackSetup(properties);
            log.info("Starting disposable destructive-lane Security Server stack");
            this.setup.start();
            var uiMapping = setup.getContainerMapping(DestructiveStackSetup.UI, Port.UI);
            var uiBaseUrl = "https://%s:%d".formatted(uiMapping.host(), uiMapping.port());
            log.info("Seeding Security Server baseline on disposable stack");
            new SsBaselineSeeder(uiBaseUrl).ensureBaseline();
        }

        private DestructiveStackSetup setup() {
            return setup;
        }

        @Override
        public void close() {
            log.info("Tearing down disposable destructive-lane Security Server stack");
            setup.stop();
        }
    }
}
