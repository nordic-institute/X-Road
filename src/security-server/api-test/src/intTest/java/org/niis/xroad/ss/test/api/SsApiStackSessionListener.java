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
package org.niis.xroad.ss.test.api;

import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.niis.xroad.ss.test.api.seeding.SsBaselineSeeder;
import org.niis.xroad.test.apitest.core.config.ApiTestConfigSource;

/**
 * Boots the Security Server stack once per JVM launcher session and tears it down when the session
 * closes. Registered via SPI so the stack is available before any suite or test class runs.
 * {@link ApiStackExtension} reads the stack via {@link #getSetup()} and {@link #getSeeder()}.
 */
@Slf4j
public class SsApiStackSessionListener implements LauncherSessionListener {

    private static volatile SsApiTestContainerSetup setup;
    private static volatile SsBaselineSeeder seeder;

    @Override
    public void launcherSessionOpened(@Nonnull LauncherSession session) {
        var properties = ApiTestConfigSource.getInstance().getCoreProperties();
        setup = new SsApiTestContainerSetup(properties);
        log.info("Starting browserless Security Server stack");
        setup.start();
        seeder = new SsBaselineSeeder(setup);
        seeder.ensureBaseline();
    }

    @Override
    public void launcherSessionClosed(@Nonnull LauncherSession session) {
        if (setup != null) {
            log.info("Stopping browserless Security Server stack");
            setup.stop();
            setup = null;
            seeder = null;
        }
    }

    static SsApiTestContainerSetup getSetup() {
        var s = setup;
        if (s == null) {
            throw new IllegalStateException(
                    "Security Server stack not started — SsApiStackSessionListener was not invoked. "
                            + "Ensure META-INF/services/org.junit.platform.launcher.LauncherSessionListener is on the classpath.");
        }
        return s;
    }

    static SsBaselineSeeder getSeeder() {
        var s = seeder;
        if (s == null) {
            throw new IllegalStateException(
                    "Security Server stack not started — SsApiStackSessionListener was not invoked. "
                            + "Ensure META-INF/services/org.junit.platform.launcher.LauncherSessionListener is on the classpath.");
        }
        return s;
    }
}
