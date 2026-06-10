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

package org.niis.xroad.messagelog;

import org.niis.xroad.common.properties.config.ConfigKey;
import org.niis.xroad.common.properties.config.ConfigKeyProvider;
import org.niis.xroad.common.properties.config.Scope;
import org.niis.xroad.messagelog.archive.GroupingStrategy;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Set;

/**
 * Message-log-encryption keys ({@code xroad.message-log-encryption.archive.*} and {@code .db.*}).
 *
 * <p>Global scope (no service name): the keys are shared by the proxy and the message-log-archiver,
 * so their DB overrides are stored with a {@code null} scope. Lives next to its domain types
 * ({@link GroupingStrategy}); the proxy and archiver register it on their own {@code XRoadConfig}.
 */
public final class MessageLogEncryptionConfigKeys implements ConfigKeyProvider {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Set<String>>> GROUPING_TYPE = new TypeReference<>() { };

    private static final Scope MESSAGE_LOG_ENCRYPTION = Scope.of("xroad.message-log-encryption");
    private static final Scope ARCHIVE = MESSAGE_LOG_ENCRYPTION.child("archive");
    private static final Scope DB = MESSAGE_LOG_ENCRYPTION.child("db");

    private static final MessageLogEncryptionConfigKeys INSTANCE = new MessageLogEncryptionConfigKeys();

    /** {@code xroad.message-log-encryption.archive.encryption-enabled}. */
    public static final ConfigKey<Boolean> ARCHIVE_ENCRYPTION_ENABLED = ARCHIVE
            .bool("encryption-enabled")
            .withDefaultValue(false)
            .build();

    /** {@code xroad.message-log-encryption.archive.default-key-id}. */
    public static final ConfigKey<String> ARCHIVE_DEFAULT_KEY_ID = ARCHIVE
            .string("default-key-id")
            .build();

    /** {@code xroad.message-log-encryption.archive.grouping-strategy}. */
    public static final ConfigKey<GroupingStrategy> ARCHIVE_GROUPING_STRATEGY = ARCHIVE
            .keyEnum("grouping-strategy", GroupingStrategy.class)
            .defaultValue(GroupingStrategy.NONE)
            .build();

    /**
     * {@code xroad.message-log-encryption.archive.grouping-keys}: member-id &rarr; key-ids, stored as a
     * single value in JSON object form (valid YAML flow syntax), e.g. {@code {"DEV:COM:1234":["k1","k2"]}}.
     */
    public static final ConfigKey<Map<String, Set<String>>> ARCHIVE_GROUPING_KEYS = ARCHIVE
            .key("grouping-keys", groupingType())
            .withConverter(MessageLogEncryptionConfigKeys::parseGroupingKeys)
            .withDefaultValue("")
            .build();

    /** {@code xroad.message-log-encryption.db.encryption-enabled}. */
    public static final ConfigKey<Boolean> DB_ENCRYPTION_ENABLED = DB
            .bool("encryption-enabled")
            .withDefaultValue(false)
            .build();

    /** {@code xroad.message-log-encryption.db.key-id}. */
    public static final ConfigKey<String> DB_KEY_ID = DB
            .string("key-id")
            .withDefaultValue("default")
            .build();

    private MessageLogEncryptionConfigKeys() {
    }

    /** @return the provider singleton (pass to {@code XRoadConfigBuilder.register(...)}). */
    public static MessageLogEncryptionConfigKeys instance() {
        return INSTANCE;
    }

    @Override
    public Scope scope() {
        return MESSAGE_LOG_ENCRYPTION;
    }

    private static Map<String, Set<String>> parseGroupingKeys(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        return MAPPER.readValue(raw, GROUPING_TYPE);
    }

    @SuppressWarnings("unchecked")
    private static Class<Map<String, Set<String>>> groupingType() {
        return (Class<Map<String, Set<String>>>) (Class<?>) Map.class;
    }
}
