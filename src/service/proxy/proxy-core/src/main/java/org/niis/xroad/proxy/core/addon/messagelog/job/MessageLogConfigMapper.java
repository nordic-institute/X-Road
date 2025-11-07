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
package org.niis.xroad.proxy.core.addon.messagelog.job;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.messagelog.archiver.proto.MessageLogConfig;
import org.niis.xroad.messagelog.archiver.proto.StringList;
import org.niis.xroad.proxy.core.configuration.ProxyMessageLogProperties;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Maps ProxyProperties archiver configuration to MessageLogConfig proto message.
 */
@ApplicationScoped
@RequiredArgsConstructor
public class MessageLogConfigMapper {
    private final ProxyMessageLogProperties messageLogProperties;

    /**
     * Builds MessageLogConfig from proxy properties.
     *
     * @return MessageLogConfig proto message
     */
    public MessageLogConfig buildMessageLogConfig() {
        var archiverProps = messageLogProperties.archiver();
        var databaseEncryptionProps = messageLogProperties.databaseEncryption();

        MessageLogConfig.Builder builder = MessageLogConfig.newBuilder()
                .setArchiveEncryptionEnabled(archiverProps.enabled())
                .setArchiveGroupingStrategy(archiverProps.groupingStrategy().name())
                .setCleanTransactionBatchSize(archiverProps.cleanTransactionBatchSize())
                .setCleanKeepRecordsFor(archiverProps.cleanKeepRecordsFor())
                .setArchiveTransactionBatchSize(archiverProps.transactionBatchSize())
                .setArchivePath(archiverProps.archivePath())
                .setGroupingStrategy(archiverProps.groupingStrategy().name())
                .setDigestAlgorithm(messageLogProperties.hashAlgoIdStr())
                .setArchiveMaxFilesize(archiverProps.maxFilesize())
                .setTmpDir(archiverProps.archivePath())
                .setMessagelogEncryptionEnabled(databaseEncryptionProps.enabled());

        // Add optional fields
        archiverProps.defaultKeyId().ifPresent(builder::setArchiveEncryptionDefaultKeyId);

        archiverProps.archiveTransferCommand().ifPresent(builder::setArchiveTransferCommand);
        databaseEncryptionProps.messagelogKeystoreStr().ifPresent(builder::setMessagelogKeystore);
        databaseEncryptionProps.messagelogKeystorePasswordStr().ifPresent(builder::setMessagelogKeystorePassword);
        databaseEncryptionProps.messagelogKeyId().ifPresent(builder::setMessagelogKeyId);

        // Convert archive grouping map
        Map<String, StringList> archiveGrouping = archiverProps.grouping().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> StringList.newBuilder().addAllValues(entry.getValue()).build()
                ));
        builder.putAllArchiveGrouping(archiveGrouping);

        return builder.build();
    }
}

