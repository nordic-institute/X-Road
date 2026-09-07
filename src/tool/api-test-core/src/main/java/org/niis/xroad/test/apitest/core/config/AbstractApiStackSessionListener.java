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
package org.niis.xroad.test.apitest.core.config;

import jakarta.annotation.Nonnull;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.niis.xroad.test.apitest.core.container.BaseComposeSetup;
import org.niis.xroad.test.apitest.core.report.AllureReportHook;

/**
 * Base {@link LauncherSessionListener} that manages the shared product stack lifecycle. Subclasses
 * implement {@link #buildAndStartSetup} to construct and start their concrete setup, and
 * {@link #buildAndEnsureBaseline} to construct and seed the baseline. An optional
 * {@link #afterBaseline} hook handles any product-specific post-seeding steps. The base stores the
 * instances in {@link ApiStackHolder} so {@link org.niis.xroad.test.apitest.core.junit.ApiStackExtension}
 * can resolve them as test parameters.
 */
public abstract class AbstractApiStackSessionListener implements LauncherSessionListener {

    @Override
    public final void launcherSessionOpened(@Nonnull LauncherSession session) {
        var setup = buildAndStartSetup();
        var seeder = buildAndEnsureBaseline(setup);
        ApiStackHolder.set(setup, seeder);
        afterBaseline(setup);
    }

    @Override
    public final void launcherSessionClosed(@Nonnull LauncherSession session) {
        AllureReportHook.generateReport();
        var setup = ApiStackHolder.setup();
        if (setup instanceof BaseComposeSetup composeSetup) {
            composeSetup.stop();
        }
        ApiStackHolder.clear();
    }

    /**
     * Constructs the product's compose setup and calls {@code start()} on it.
     *
     * @return the running setup instance
     */
    protected abstract BaseComposeSetup buildAndStartSetup();

    /**
     * Constructs the product's baseline seeder and calls {@code ensureBaseline()} on it.
     *
     * @param setup the running setup returned by {@link #buildAndStartSetup}
     * @return the seeder instance
     */
    protected abstract Object buildAndEnsureBaseline(BaseComposeSetup setup);

    /**
     * Called after baseline seeding completes. Override for product-specific post-seeding steps.
     * Default implementation is a no-op.
     *
     * @param setup the running setup
     */
    protected void afterBaseline(BaseComposeSetup setup) {
    }
}
