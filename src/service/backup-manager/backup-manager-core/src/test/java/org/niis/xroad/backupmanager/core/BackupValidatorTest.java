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

package org.niis.xroad.backupmanager.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.properties.ConfigUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BackupValidatorTest {

    BackupManagerProperties backupManagerProperties = ConfigUtils.defaultConfiguration(BackupManagerProperties.class);

    BackupValidator validator = new BackupValidator(backupManagerProperties);

    @BeforeEach
    void setUp() {
        validator.init();
    }

    @Test
    void invalidBackupFilename() {
        assertFalse(validator.isValidBackupFilename("/b.gpg"));
        assertFalse(validator.isValidBackupFilename("../b.gpg"));
        assertFalse(validator.isValidBackupFilename("a/b.gpg"));
    }

    @Test
    void validBackupFilename() {
        assertThat(validator.isValidBackupFilename("b.gpg")).isTrue();
        assertThat(validator.isValidBackupFilename("ss-automatic-backup-2025_05_15_141500.gpg")).isTrue();
        assertThat(validator.isValidBackupFilename("conf_backup_20250515-134650.gpg")).isTrue();
    }
}
