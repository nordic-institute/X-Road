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
package ee.ria.xroad.proxy;

import ee.ria.xroad.common.MessageLogArchiveEncryptionMember;
import ee.ria.xroad.common.MessageLogEncryptionStatusDiagnostics;
import ee.ria.xroad.common.conf.serverconf.ServerConfProvider;
import ee.ria.xroad.common.db.DatabaseCtxV2;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.messagelog.AbstractLogManager;
import ee.ria.xroad.common.messagelog.MessageLogConfig;
import ee.ria.xroad.common.messagelog.MessageLogProperties;
import ee.ria.xroad.common.messagelog.archive.EncryptionConfigProvider;
import ee.ria.xroad.common.messagelog.archive.GroupingStrategy;
import ee.ria.xroad.proxy.messagelog.MessageLog;

import io.quarkus.runtime.Startup;
import io.smallrye.config.ConfigMapping;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.proxy.ProxyAddonProperties;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
public class ProxyMessageLogConfig {
    private static final GroupingStrategy ARCHIVE_GROUPING = MessageLogProperties.getArchiveGrouping();

    @Produces
    @Startup
    MessageLog messageLogManager(AbstractLogManager logManager) {
        return MessageLog.init(logManager);
    }


    @ApplicationScoped
    @ConfigMapping(prefix = "xroad.messagelog")
    public static class SpringMessageLogProperties extends MessageLogConfig {
    }

    @Produces
    @ApplicationScoped
    DatabaseCtxV2 messagelogDatabaseCtx(ProxyAddonProperties proxyAddonProperties,
                                        MessageLogConfig messageLogProperties) {
        if (proxyAddonProperties.messagelog().enabled()) {
            return new DatabaseCtxV2("messagelog", messageLogProperties.getHibernate());
        }
        return null;
    }

    @Produces
    @ApplicationScoped
    MessageLogEncryptionStatusDiagnostics messageLogEncryptionStatusDiagnostics(ServerConfProvider serverConfProvider) throws IOException {
        return new MessageLogEncryptionStatusDiagnostics(
                MessageLogProperties.isArchiveEncryptionEnabled(),
                MessageLogProperties.isMessageLogEncryptionEnabled(),
                ARCHIVE_GROUPING.name(),
                getMessageLogArchiveEncryptionMembers(getMembers(serverConfProvider)));
    }

    private List<ClientId> getMembers(ServerConfProvider serverConfProvider) {
        try {
            return new ArrayList<>(serverConfProvider.getMembers());
        } catch (Exception e) {
            log.warn("Failed to get members from server configuration", e);
            return Collections.emptyList();
        }
    }

    private static List<MessageLogArchiveEncryptionMember> getMessageLogArchiveEncryptionMembers(
            List<ClientId> members) throws IOException {
        EncryptionConfigProvider configProvider = EncryptionConfigProvider.getInstance(ARCHIVE_GROUPING);
        if (!configProvider.isEncryptionEnabled()) {
            return Collections.emptyList();
        }
        return configProvider.forDiagnostics(members).getEncryptionMembers()
                .stream()
                .map(member -> new MessageLogArchiveEncryptionMember(member.getMemberId(),
                        member.getKeys(), member.isDefaultKeyUsed()))
                .toList();
    }

}
