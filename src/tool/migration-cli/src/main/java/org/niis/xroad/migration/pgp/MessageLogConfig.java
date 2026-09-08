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
package org.niis.xroad.migration.pgp;

import lombok.Builder;
import lombok.Getter;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * Configuration for message log archive encryption from local.ini file.
 */
@Getter
@Builder
public class MessageLogConfig {
    /**
     * Whether archive encryption is enabled.
     */
    private final boolean archiveEncryptionEnabled;

    /**
     * Archive grouping strategy: none, member, or subsystem.
     */
    private final String archiveGrouping;

    /**
     * Path to GPG home directory containing keys.
     */
    private final Path archiveGpgHomeDirectory;

    /**
     * Default encryption key ID (used when grouping is 'none' or as fallback).
     */
    private final String archiveDefaultEncryptionKey;

    /**
     * Path to encryption keys mapping file (for member/subsystem grouping).
     */
    private final Path archiveEncryptionKeysConfig;

    /**
     * Member/subsystem to key ID mappings (parsed from config file).
     * Each member can have multiple encryption keys (Set of key IDs).
     */
    private final Map<String, Set<String>> encryptionKeyMappings;

    /**
     * Checks if archive encryption is disabled.
     */
    public boolean isEncryptionDisabled() {
        return !archiveEncryptionEnabled;
    }

    /**
     * Checks if grouping is enabled (member or subsystem level).
     */
    public boolean hasGrouping() {
        return !"none".equalsIgnoreCase(archiveGrouping) && archiveGrouping != null;
    }

    /**
     * Returns true if this is member-level grouping.
     */
    public boolean isMemberGrouping() {
        return "member".equalsIgnoreCase(archiveGrouping);
    }

    /**
     * Returns true if this is subsystem-level grouping.
     */
    public boolean isSubsystemGrouping() {
        return "subsystem".equalsIgnoreCase(archiveGrouping);
    }
}

