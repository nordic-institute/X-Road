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
package ee.ria.xroad.common.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class BackupUtilsTest {

    @Test
    void generateBackupFileName() {
        TimeUtils.setClock(Clock.fixed(Instant.parse("2025-05-15T01:02:03Z"), ZoneOffset.UTC));
        assertThat(BackupUtils.generateBackupFileName()).isEqualTo("conf_backup_v1_20250515-010203.gpg");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "conf_backup_v1_20250515-010203.gpg",
            "backup_v1_.gpg",
            "_v1_file.gpg"
    })
    void isBackupCompatible(String filename) {
        assertThat(BackupUtils.isBackupCompatible(filename)).isTrue();
        assertThat(BackupUtils.isBackupCompatible(Path.of(filename))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "conf_backup_v2_20250515-010203.gpg",
            "conf_backup_20250515-010203.gpg",
            "backup_v10_.gpg",
            "backup_v1x_.gpg",
            "backup.gpg"
    })
    void isBackupCompatibilityBackupIncompatible(String filename) {
        assertThat(BackupUtils.isBackupCompatible(filename)).isFalse();
        assertThat(BackupUtils.isBackupCompatible(Path.of(filename))).isFalse();
    }
}
