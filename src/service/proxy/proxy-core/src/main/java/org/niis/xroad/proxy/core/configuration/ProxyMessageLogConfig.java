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
package org.niis.xroad.proxy.core.configuration;

import ee.ria.xroad.common.messagelog.AbstractLogManager;
import ee.ria.xroad.messagelog.database.MessageLogDatabaseCtx;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.messagelog.MessageLogArchivalProperties;
import org.niis.xroad.common.messagelog.MessageLogDatabaseEncryptionProperties;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.proxy.core.addon.messagelog.LogManager;
import org.niis.xroad.proxy.core.addon.messagelog.LogRecordManager;
import org.niis.xroad.proxy.core.messagelog.MessageLog;
import org.niis.xroad.proxy.core.messagelog.NullLogManager;
import org.niis.xroad.serverconf.ServerConfProvider;

@Slf4j
public class ProxyMessageLogConfig {

    @ApplicationScoped
    public static class MessageLogInitializer {

        @Startup
        @ApplicationScoped
        AbstractLogManager messageLogManager(ProxyMessageLogProperties messageLogProperties,
                                             GlobalConfProvider globalConfProvider,
                                             ServerConfProvider serverConfProvider,
                                             MessageLogDatabaseCtx messageLogDatabaseCtx,
                                             LogRecordManager logRecordManager) {
            AbstractLogManager logManager;
            if (messageLogProperties.enabled()) {
                logManager = new LogManager(globalConfProvider, serverConfProvider, logRecordManager, messageLogDatabaseCtx,
                        messageLogProperties);
            } else {
                logManager = new NullLogManager(globalConfProvider, serverConfProvider);
            }

            return MessageLog.init(logManager);
        }

        public void cleanup(@Disposes AbstractLogManager logManager) {
            if (logManager instanceof LogManager impl)
                impl.destroy();
        }
    }

    @ApplicationScoped
    MessageLogArchivalProperties messageLogArchivalProperties(ProxyMessageLogProperties messageLogProperties) {
        return messageLogProperties.archiver();
    }

    @ApplicationScoped
    MessageLogDatabaseEncryptionProperties messageLogEncryptionProperties(ProxyMessageLogProperties messageLogProperties) {
        return messageLogProperties.databaseEncryption();
    }
}
