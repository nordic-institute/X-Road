/**
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

import ee.ria.xroad.common.messagelog.MessageLogProperties;
import ee.ria.xroad.common.util.CryptoUtils;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

interface EncryptionConfigProvider {

    default boolean isEncryptionEnabled() {
        return true;
    }

    /**
     * Given a grouping, returns an encryption configuration that applies to it.
     */
    EncryptionConfig forGrouping(Grouping grouping) throws IOException;

    static EncryptionConfigProvider getInstance(GroupingStrategy groupingStrategy) {
        if (!MessageLogProperties.getArchiveEncryptionEnabled()) {
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
}

/**
 * Encrypts using the security server key
 */
@Slf4j
final class ServerEncryptionConfigProvider implements EncryptionConfigProvider {
    private final Path gpgHome = MessageLogProperties.getGPGHome();
    private final EncryptionConfig config;

    ServerEncryptionConfigProvider() {
        final Path key = MessageLogProperties.getArchiveDefaultEncryptionKey();
        final List<Path> defaultKey;
        if (key == null) {
            log.warn("Default archive encryption key not defined, using primary GPG key as default.");
            defaultKey = Collections.emptyList();
        } else {
            defaultKey = Collections.singletonList(MessageLogProperties.getArchiveEncryptionKeysDir().resolve(key));
        }
        config = new EncryptionConfig(true, gpgHome, defaultKey);
    }

    @Override
    public EncryptionConfig forGrouping(Grouping grouping) {
        return config;
    }
}

/**
 * Encrypts using per-member key, or security server key if the
 * member key is not available
 */
@Slf4j
final class MemberEncryptionConfigProvider implements EncryptionConfigProvider {

    private final Path gpgHome = MessageLogProperties.getGPGHome();
    private final Path keyDir = MessageLogProperties.getArchiveEncryptionKeysDir();
    private final List<Path> defaultKey;
    private final MessageDigest digest;

    MemberEncryptionConfigProvider() {
        try {
            digest = MessageDigest.getInstance("SHA-256");
            final Path key = MessageLogProperties.getArchiveDefaultEncryptionKey();
            if (key == null) {
                log.warn("Default archive encryption key not defined, using primary GPG key as default.");
                this.defaultKey = Collections.emptyList();
            } else {
                this.defaultKey = Collections.singletonList(keyDir.resolve(key));
            }
        } catch (NoSuchAlgorithmException e) {
            // should not happen
            throw new IllegalStateException("Unable to create SHA-256 message digest", e);
        }
    }

    public EncryptionConfig forGrouping(Grouping grouping) throws IOException {
        if (grouping.getClientId() == null) {
            throw new IllegalArgumentException("Expected a grouping with a client identifier");
        }

        byte[] keyDigest =
                digest.digest(grouping.getClientId().getMemberId().toShortString().getBytes(StandardCharsets.UTF_8));
        digest.reset();

        final String keyName = CryptoUtils.encodeHex(keyDigest);

        List<Path> keys = findKeys(keyName);

        if (keys.isEmpty()) {
            log.info("Encryption key {}.* does not exist, using default key for group {}", keyName, grouping);
            return new EncryptionConfig(true, gpgHome, defaultKey);
        } else {
            log.debug("Using key(s) {} for encrypting group {} archives", keys, grouping);
            return new EncryptionConfig(true, gpgHome, keys);
        }
    }

    /* Find files starting with "<keyName>." and ending with ".(pgp|asc|gpg)".

        Multiple keys can be defined e.g. by using the following convention.
            keyName.1.pgp
            keyName.2.pgp
            ..
     */
    private List<Path> findKeys(String keyName) throws IOException {
        final String prefix = keyName + ".";

        try (Stream<Path> stream = Files.find(keyDir, 1,
                (path, attr) -> attr.isRegularFile()
                        && path.getFileName().toString().startsWith(prefix)
                        && SUFFIX.matcher(path.getFileName().toString()).matches(),
                FileVisitOption.FOLLOW_LINKS)) {
            return stream.collect(Collectors.toList());
        }
    }

    private static final Pattern SUFFIX = Pattern.compile("^.+\\.(pgp|asc|gpg)$");
}
