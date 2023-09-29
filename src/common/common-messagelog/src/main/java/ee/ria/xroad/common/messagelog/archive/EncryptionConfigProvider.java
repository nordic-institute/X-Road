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
package ee.ria.xroad.common.messagelog.archive;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.messagelog.MessageLogProperties;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A strategy interface for archive encryption configuration providers.
 * @see DisabledEncryptionConfigProvider
 * @see ServerEncryptionConfigProvider
 * @see MemberEncryptionConfigProvider
 */
public interface EncryptionConfigProvider {

    default boolean isEncryptionEnabled() {
        return true;
    }

    /**
     * Given a grouping, returns an encryption configuration that applies to it.
     */
    EncryptionConfig forGrouping(Grouping grouping) throws IOException;

    /**
     * Returns encryption info for diagnostics
     */
    EncryptionConfig forDiagnostics(List<ClientId> members);

    static EncryptionConfigProvider getInstance(GroupingStrategy groupingStrategy) throws IOException {
        if (!MessageLogProperties.isArchiveEncryptionEnabled()) {
            return DisabledEncryptionConfigProvider.INSTANCE;
        } else if (groupingStrategy == GroupingStrategy.NONE) {
            return new ServerEncryptionConfigProvider();
        } else {
            return new MemberEncryptionConfigProvider();
        }
    }
}

enum DisabledEncryptionConfigProvider implements EncryptionConfigProvider {
    INSTANCE;

    @Override
    public boolean isEncryptionEnabled() {
        return false;
    }

    @Override
    public EncryptionConfig forGrouping(Grouping grouping) {
        return EncryptionConfig.DISABLED;
    }

    @Override
    public EncryptionConfig forDiagnostics(List<ClientId> members) {
        return EncryptionConfig.DISABLED;
    }
}

/**
 * Provides encryption configuration that uses the security server key
 */
@Slf4j
final class ServerEncryptionConfigProvider implements EncryptionConfigProvider {
    private final Path gpgHome = MessageLogProperties.getArchiveGPGHome();
    private final EncryptionConfig config;

    ServerEncryptionConfigProvider() {
        final String key = MessageLogProperties.getArchiveDefaultEncryptionKey();
        final Set<String> defaultKey;
        if (key == null) {
            log.warn("Default archive encryption key not defined, using primary GPG key as default.");
            defaultKey = Collections.emptySet();
        } else {
            defaultKey = Collections.singleton(key);
        }
        config = new EncryptionConfig(true, gpgHome, defaultKey, Collections.emptyList());
    }

    @Override
    public EncryptionConfig forGrouping(Grouping grouping) {
        return config;
    }

    @Override
    public EncryptionConfig forDiagnostics(List<ClientId> members) {
        return config;
    }
}

/**
 * Provides encryption configuration that uses member keys, or security server key if a
 * member key is not available
 */
@Slf4j
final class MemberEncryptionConfigProvider implements EncryptionConfigProvider {

    private final Path gpgHome = MessageLogProperties.getArchiveGPGHome();
    private final Set<String> defaultKey;
    private final Map<String, Set<String>> keyMappings;

    MemberEncryptionConfigProvider() throws IOException {
        Path keyMapping = MessageLogProperties.getArchiveEncryptionKeysConfig();
        keyMappings = readKeyMappings(keyMapping);
        final String key = MessageLogProperties.getArchiveDefaultEncryptionKey();
        if (key == null) {
            log.warn("Default archive encryption key not defined, using primary GPG key as default.");
            this.defaultKey = Collections.emptySet();
        } else {
            this.defaultKey = Collections.singleton(key);
        }
    }

    public EncryptionConfig forGrouping(Grouping grouping) {
        if (grouping.getClientId() == null) {
            throw new IllegalArgumentException("Expected a grouping with a client identifier");
        }

        Set<String> keys = keyMappings.get(grouping.getClientId().getMemberId().toShortString());

        if (keys == null || keys.isEmpty()) {
            log.info("Encryption mapping does not exist, using default key for group {}", grouping);
            return new EncryptionConfig(true, gpgHome, defaultKey, Collections.emptyList());
        } else {
            log.debug("Using key(s) {} for encrypting group {} archives", keys, grouping);
            return new EncryptionConfig(true, gpgHome, keys, Collections.emptyList());
        }
    }

    @Override
    public EncryptionConfig forDiagnostics(List<ClientId> members) {
        return new EncryptionConfig(true, gpgHome, Collections.emptySet(), getEncryptionMembers(members));
    }

    private List<EncryptionMember> getEncryptionMembers(List<ClientId> members) {
        return members.stream()
                .map(member -> member.getMemberId().toShortString())
                .distinct()
                .map(this::getEncryptionMember)
                .collect(Collectors.toList());
    }

    private EncryptionMember getEncryptionMember(String memberId) {
        Set<String> keys = keyMappings.get(memberId);

        if (keys == null || keys.isEmpty()) {
            log.info("Encryption mapping does not exist, using default key for member {}", memberId);
            return new EncryptionMember(memberId, defaultKey, true);
        } else {
            log.debug("Using key(s) {} for encrypting member {}", keys, memberId);
            return new EncryptionMember(memberId, keys, false);
        }
    }

    /*
     * Reads a mapping file in format
     * <pre>
     * #comment on its own line is ignored
     * memberidentifier=keyid
     * memberidentifier= keyid2
     * another\=member = =this is a valid key id#not a comment
     * </pre>
     * and returns the mappings.
     *
     * A member identifier can be listed multiple times.
     *
     * If the member identifier contains '=' it can be escaped using '\='.
     * A literal '\=' must be written as '\\='
     *
     * If the member identifier starts with '#', it can be escaped using '\#'
     * A literal '\#' in member identifier must be written as '\\#'
     */
    static Map<String, Set<String>> readKeyMappings(Path mappingFile) throws IOException {
        if (mappingFile != null && Files.exists(mappingFile)) {
            final Map<String, Set<String>> keyMappings = new HashMap<>();
            try {
                final List<String> lines = Files.readAllLines(mappingFile);
                for (int i = 0; i < lines.size(); i++) {
                    final String line = lines.get(i);
                    if (line.isEmpty() || COMMENT.matcher(line).matches()) {
                        continue;
                    }
                    final String[] mapping = SPLITTER.split(line, 2);
                    if (mapping.length != 2 || mapping[0].trim().isEmpty() || mapping[1].trim().isEmpty()) {
                        log.warn("Invalid gpg key mapping at {}:{} ignored", mappingFile, i + 1);
                        continue;
                    }
                    final String identifier = mapping[0].trim().replace("\\=", "=").replace("\\#", "#");
                    final String keyId = mapping[1].trim();
                    keyMappings.computeIfAbsent(identifier, k -> new HashSet<>()).add(keyId);
                }
                return keyMappings;
            } catch (IOException e) {
                log.error("Unable to read member identifier to gpg key mapping file", e);
                throw e;
            }
        }
        return Collections.emptyMap();
    }

    private static final Pattern SPLITTER = Pattern.compile("\\s*(?<!\\\\)=\\s*");
    private static final Pattern COMMENT = Pattern.compile("^\\s*(?!\\\\)#.*$");
}
