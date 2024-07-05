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
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.messagelog.AbstractLogManager;
import ee.ria.xroad.common.messagelog.MessageLogProperties;
import ee.ria.xroad.common.messagelog.archive.EncryptionConfigProvider;
import ee.ria.xroad.common.messagelog.archive.GroupingStrategy;
import ee.ria.xroad.common.util.JobManager;
import ee.ria.xroad.proxy.messagelog.MessageLog;
import ee.ria.xroad.proxy.messagelog.NullLogManager;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Configuration
public class ProxyMessageLogConfig {
    private static final GroupingStrategy ARCHIVE_GROUPING = MessageLogProperties.getArchiveGrouping();

    @Bean(destroyMethod = "shutdown")
    AbstractLogManager messageLogManager(JobManager jobManager) {
        return MessageLog.init(jobManager);
    }

    @Bean("messageLogEnabledStatus")
    Boolean messageLogEnabledStatus(AbstractLogManager logManager) {
        return NullLogManager.class != logManager.getClass();
    }

    @Bean
    MessageLogEncryptionStatusDiagnostics messageLogEncryptionStatusDiagnostics() throws IOException {
        return new MessageLogEncryptionStatusDiagnostics(
                MessageLogProperties.isArchiveEncryptionEnabled(),
                MessageLogProperties.isMessageLogEncryptionEnabled(),
                ARCHIVE_GROUPING.name(),
                getMessageLogArchiveEncryptionMembers(getMembers()));
    }

    private static List<ClientId> getMembers() {
        try {
            return new ArrayList<>(ServerConf.getMembers());
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
