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
package ee.ria.xroad.messagelog.archiver;

import ee.ria.xroad.common.SystemPropertiesLoader;
import ee.ria.xroad.common.Version;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

import static ee.ria.xroad.common.SystemProperties.CONF_FILE_MESSAGE_LOG;
import static ee.ria.xroad.common.SystemProperties.CONF_FILE_NODE;

@Slf4j
public final class LogArchiverMain {
    private static final String APP_NAME = "MessageLogArchiver";


    private LogArchiverMain() {
    }

    public static void main(String[] args) {
        try {
            new LogArchiverMain().createApplicationContext();
        } catch (Exception e) {
            log.error("LogArchiver failed to start", e);
            System.exit(1);
        }
    }

    public GenericApplicationContext createApplicationContext(Class<?>... ctxExtension) {
        var startTime = System.currentTimeMillis();
        Version.outputVersionInfo(APP_NAME);
        log.info("Starting {} ({})...", APP_NAME, Version.XROAD_VERSION);

        log.trace("Loading global bean dependencies");
        loadSystemProperties();

        var springCtx = new AnnotationConfigApplicationContext();
        springCtx.register(LogArchiverConfig.class);
        if (ctxExtension.length > 0) {
            springCtx.register(ctxExtension);
        }
        springCtx.refresh();
        springCtx.registerShutdownHook();
        log.info("{} started in {} ms", APP_NAME, System.currentTimeMillis() - startTime);
        return springCtx;
    }

    private void loadSystemProperties() {
        SystemPropertiesLoader.create()
                .withCommonAndLocal()
                .with(CONF_FILE_MESSAGE_LOG)
                .withLocalOptional(CONF_FILE_NODE)
                .load();
    }


}
