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
package org.niis.xroad.common.vault;

import org.junit.jupiter.api.Test;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.niis.xroad.common.core.exception.ErrorCode.SECRET_STORE_UNAVAILABLE;

class MessageLogVaultDataUtilsTest {

    private static final Function<String, List<String>> LISTS_ONE_AND_TWO = basePath -> List.of("1", "2");

    @Test
    void shouldRethrowAnOutageWhileListingTheKeys() {
        Function<String, List<String>> lists = basePath -> {
            throw outage();
        };

        assertThatThrownBy(() -> MessageLogVaultDataUtils.getMLogDBEncryptionSecretKeys(lists, path -> Optional.empty()))
                .isInstanceOfSatisfying(XrdRuntimeException.class,
                        e -> assertThat(e.isCausedBy(SECRET_STORE_UNAVAILABLE)).isTrue());
    }

    @Test
    void shouldRethrowAnOutageWhileReadingAKey() {
        Function<String, Optional<? extends Map<String, ?>>> reads = path -> {
            throw outage();
        };

        assertThatThrownBy(() -> MessageLogVaultDataUtils.getMLogDBEncryptionSecretKeys(LISTS_ONE_AND_TWO, reads))
                .isInstanceOfSatisfying(XrdRuntimeException.class,
                        e -> assertThat(e.isCausedBy(SECRET_STORE_UNAVAILABLE)).isTrue());
    }

    @Test
    void shouldReturnThePartialMapWhenAnotherFailureStopsTheReading() {
        var firstPath = MessageLogVaultDataUtils.buildEncryptionKeyPath("1");
        Function<String, Optional<? extends Map<String, ?>>> reads = path -> {
            if (path.equals(firstPath)) {
                return Optional.of(Map.of(VaultClient.PAYLOAD_KEY, "key-1"));
            }
            throw new IllegalStateException("vault sealed");
        };

        var keys = MessageLogVaultDataUtils.getMLogDBEncryptionSecretKeys(LISTS_ONE_AND_TWO, reads);

        assertThat(keys).containsExactlyEntriesOf(Map.of("1", "key-1"));
    }

    @Test
    void shouldReturnAnEmptyMapWhenListingFailsForAnotherReason() {
        Function<String, List<String>> lists = basePath -> {
            throw new IllegalStateException("vault sealed");
        };

        var keys = MessageLogVaultDataUtils.getMLogDBEncryptionSecretKeys(lists, path -> Optional.empty());

        assertThat(keys).isEmpty();
    }

    private static XrdRuntimeException outage() {
        return XrdRuntimeException.systemException(SECRET_STORE_UNAVAILABLE, new IllegalStateException("down"),
                "Secret store is down");
    }
}
