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
package org.niis.xroad.opmonitor.application;

import ee.ria.xroad.common.SystemPropertiesLoader;
import ee.ria.xroad.common.Version;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.opmonitor.core.config.OpMonitorDaemonRootConfig;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

import static ee.ria.xroad.common.SystemProperties.CONF_FILE_OP_MONITOR;

/**
 * The main class of the operational monitoring daemon.
 * This class is responsible for creating the request handlers for receiving
 * and providing monitoring data.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OpMonitorDaemonMain {
    private static final String APP_NAME = "xroad-opmonitor";

    /**
     * Main entry point of the daemon.
     *
     * @param args command-line arguments
     * @throws Exception in case of any errors
     */
    public static void main(String[] args) {
        try {
            new OpMonitorDaemonMain().createApplicationContext();
        } catch (Exception e) {
            log.error("Operational monitoring daemon failed to start", e);
            throw e;
        }
    }

    private GenericApplicationContext createApplicationContext() {
        var startTime = System.currentTimeMillis();
        log.info("Starting the operational monitoring daemon");
        Version.outputVersionInfo(APP_NAME);

        SystemPropertiesLoader.create().withCommonAndLocal()
                .with(CONF_FILE_OP_MONITOR, "op-monitor")
                .load();
        log.info("Loaded system properties...");

        var springCtx = new AnnotationConfigApplicationContext();
        springCtx.register(OpMonitorDaemonRootConfig.class);
        springCtx.refresh();
        springCtx.registerShutdownHook();

        log.info("{} started in {} ms", APP_NAME, System.currentTimeMillis() - startTime);
        return springCtx;
    }

}

