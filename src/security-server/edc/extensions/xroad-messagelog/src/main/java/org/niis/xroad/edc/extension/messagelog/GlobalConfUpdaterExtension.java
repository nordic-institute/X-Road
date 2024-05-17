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

package org.niis.xroad.edc.extension.messagelog;

import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static java.util.concurrent.TimeUnit.SECONDS;

@Extension(value = GlobalConfUpdaterExtension.NAME)
// todo: move to separate extension?
public class GlobalConfUpdaterExtension implements ServiceExtension {

    static final String NAME = "GlobalConf updater extension";

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

    @Setting
    private static final String XROAD_GLOBAL_CONF_RELOAD_INTERVAL = "xroad.globalconf.reload.interval";

    private static final int DEFAULT_RELOAD_INTERVAL_SECONDS = 60;

    @Override
    public void initialize(ServiceExtensionContext context) {
        var monitor = context.getMonitor();
        GlobalConfUpdater globalConfUpdater = new GlobalConfUpdater(monitor);

        int reloadIntervalSeconds = context.getSetting(XROAD_GLOBAL_CONF_RELOAD_INTERVAL, DEFAULT_RELOAD_INTERVAL_SECONDS);

        executor.scheduleWithFixedDelay(globalConfUpdater::update, reloadIntervalSeconds, reloadIntervalSeconds, SECONDS);
    }

    @Override
    public void shutdown() {
        executor.shutdown();
    }
}
