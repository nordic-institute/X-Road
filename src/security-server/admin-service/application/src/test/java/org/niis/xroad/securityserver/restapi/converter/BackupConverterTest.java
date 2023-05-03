/**
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.securityserver.restapi.converter;

import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.restapi.common.backup.dto.BackupFile;
import org.niis.xroad.securityserver.restapi.openapi.model.Backup;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * Test BackupConverter
 */
public class BackupConverterTest {

    private BackupConverter backupConverter;

    private static final String BACKUP_FILE_1 = "ss-automatic-backup-2020_02_19_031502.tar";

    private static final String BACKUP_FILE_2 = "ss-automatic-backup-2020_02_12_031502.tar";

    private static final String BACKUP_FILE_3 = "ss-automatic-backup-2019_12_31_031502.tar";

    private static final OffsetDateTime DEFAULT_CREATED_TIME = Instant.now().atOffset(ZoneOffset.UTC);

    @Before

    public void setup() {
        backupConverter = new BackupConverter();
    }

    @Test
    public void convertSingleBackup() {
        Backup backup = backupConverter.convert(new BackupFile(BACKUP_FILE_1, DEFAULT_CREATED_TIME));

        assertEquals(BACKUP_FILE_1, backup.getFilename());
    }

    @Test
    public void convertMultipleBackups() {
        List<BackupFile> files = new ArrayList<>(Arrays.asList(new BackupFile(BACKUP_FILE_1, DEFAULT_CREATED_TIME),
                new BackupFile(BACKUP_FILE_2, DEFAULT_CREATED_TIME),
                new BackupFile(BACKUP_FILE_3, DEFAULT_CREATED_TIME)));
        Set<Backup> backups = backupConverter.convert(files);

        assertEquals(3, backups.size());
    }

    @Test
    public void convertMEmptyList() {
        List<BackupFile> files = new ArrayList<>();
        Set<Backup> backups = backupConverter.convert(files);

        assertEquals(0, backups.size());
    }
}
