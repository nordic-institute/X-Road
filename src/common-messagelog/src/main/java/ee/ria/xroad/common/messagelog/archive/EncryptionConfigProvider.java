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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;

interface EncryptionConfigProvider {

    default boolean isEncryptionEnabled() {
        return true;
    }

    /**
     * Given a grouping, returns an encryption configuration that applies to it.
     */
    EncryptionConfig forGrouping(Grouping grouping);

    static EncryptionConfigProvider getInstance(GroupingStrategy groupingStrategy) {
        if (!MessageLogProperties.isEncryptionEnabled()) {
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
class ServerEncryptionConfigProvider implements EncryptionConfigProvider {
    private final Path gpgHome = MessageLogProperties.getGPGHome();
    private final EncryptionConfig config = new EncryptionConfig(true, gpgHome, null);

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
class MemberEncryptionConfigProvider implements EncryptionConfigProvider {

    private final Path gpgHome = MessageLogProperties.getGPGHome();
    private final Path keyDir = MessageLogProperties.getEncryptionKeysDir();
    private final MessageDigest digest;

    MemberEncryptionConfigProvider() {
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unable to create SHA-256 message digest", e);
        }
    }

    public EncryptionConfig forGrouping(Grouping grouping) {
        if (grouping.getClientId() == null) {
            throw new IllegalArgumentException("Expected a grouping with a client identifier");
        }

        log.debug(grouping.getClientId().getMemberId().toShortString());
        byte[] keyName = digest.digest(
                grouping.getClientId().getMemberId().toShortString().getBytes(StandardCharsets.UTF_8));
        digest.reset();

        Path key = keyDir.resolve(CryptoUtils.encodeHex(keyName) + ".pgp");

        if (Files.exists(key)) {
            log.debug("Using key {} for grouping {}", key, grouping);
            return new EncryptionConfig(true, gpgHome, Collections.singletonList(key));
        } else {
            log.debug("Key {} does not exist, using server key for grouping {}", key, grouping);
            return new EncryptionConfig(true, gpgHome, null);
        }
    }
}
