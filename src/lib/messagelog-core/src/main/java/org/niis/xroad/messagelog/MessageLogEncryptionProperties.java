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
package org.niis.xroad.messagelog;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.messagelog.archive.GroupingStrategy;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.niis.xroad.messagelog.MessageLogEncryptionConfigKeys.ARCHIVE_DEFAULT_KEY_ID;
import static org.niis.xroad.messagelog.MessageLogEncryptionConfigKeys.ARCHIVE_ENCRYPTION_ENABLED;
import static org.niis.xroad.messagelog.MessageLogEncryptionConfigKeys.ARCHIVE_GROUPING_KEYS;
import static org.niis.xroad.messagelog.MessageLogEncryptionConfigKeys.ARCHIVE_GROUPING_STRATEGY;
import static org.niis.xroad.messagelog.MessageLogEncryptionConfigKeys.DB_ENCRYPTION_ENABLED;
import static org.niis.xroad.messagelog.MessageLogEncryptionConfigKeys.DB_KEY_ID;

/**
 * Message log encryption properties.
 */
@RequiredArgsConstructor
public class MessageLogEncryptionProperties {

    private final XRoadConfig xRoadConfig;
    private final ArchiveEncryptionConfig archive;
    private final DbEncryptionConfig db;

    public MessageLogEncryptionProperties(XRoadConfig xRoadConfig) {
        this.xRoadConfig = xRoadConfig;
        this.archive = new ArchiveEncryptionConfig(xRoadConfig);
        this.db = new DbEncryptionConfig(xRoadConfig);
    }

    public ArchiveEncryptionConfig archive() {
        return archive;
    }

    public DbEncryptionConfig db() {
        return db;
    }

    @RequiredArgsConstructor
    public static class ArchiveEncryptionConfig {

        private final XRoadConfig xRoadConfig;

        public boolean encryptionEnabled() {
            return xRoadConfig.value(ARCHIVE_ENCRYPTION_ENABLED);
        }

        public Optional<String> defaultKeyId() {
            return xRoadConfig.valueOpt(ARCHIVE_DEFAULT_KEY_ID);
        }

        public GroupingStrategy groupingStrategy() {
            return xRoadConfig.value(ARCHIVE_GROUPING_STRATEGY);
        }

        public Map<String, Set<String>> grouping() {
            return xRoadConfig.value(ARCHIVE_GROUPING_KEYS);
        }
    }

    @RequiredArgsConstructor
    public static class DbEncryptionConfig {

        private final XRoadConfig xRoadConfig;

        public boolean enabled() {
            return xRoadConfig.value(DB_ENCRYPTION_ENABLED);
        }

        public String keyId() {
            return xRoadConfig.value(DB_KEY_ID);
        }
    }

}
