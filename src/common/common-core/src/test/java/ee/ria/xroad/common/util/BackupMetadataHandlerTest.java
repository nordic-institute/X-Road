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

import ee.ria.xroad.common.util.process.ExternalProcessRunner;
import ee.ria.xroad.common.util.process.ProcessFailedException;
import ee.ria.xroad.common.util.process.ProcessNotExecutableException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BackupMetadataHandlerTest {

    private static final String FORMAT_VERSION = "v1";
    private static final String SERVER_TYPE = "central";

    @TempDir
    private Path backupDir;

    @Mock
    private ExternalProcessRunner externalProcessRunner;

    private BackupMetadataHandler handler;

    @BeforeEach
    void setUp() throws IOException {
        Path formatVersionFile = backupDir.resolve("_backup_format_version");
        Files.writeString(formatVersionFile, FORMAT_VERSION);

        handler = new BackupMetadataHandler(externalProcessRunner, formatVersionFile.toString(),
                "create-metadata-script", backupDir, SERVER_TYPE);
    }

    @Test
    void isBackupCompatibleWhenMetadataMatches() throws IOException {
        Path backupPath = writeMetadata("backup.tar", FORMAT_VERSION, SERVER_TYPE);

        assertThat(handler.isBackupCompatible(backupPath)).isTrue();
    }

    @Test
    void isBackupNotCompatibleWhenMetadataMissing() {
        Path backupPath = backupDir.resolve("backup.tar");

        assertThat(handler.isBackupCompatible(backupPath)).isFalse();
    }

    @Test
    void isBackupNotCompatibleWhenVersionDiffers() throws IOException {
        Path backupPath = writeMetadata("backup.tar", "v2", SERVER_TYPE);

        assertThat(handler.isBackupCompatible(backupPath)).isFalse();
    }

    @Test
    void isBackupNotCompatibleWhenServerTypeDiffers() throws IOException {
        Path backupPath = writeMetadata("backup.tar", FORMAT_VERSION, "security");

        assertThat(handler.isBackupCompatible(backupPath)).isFalse();
    }

    @Test
    void isBackupNotCompatibleWhenMetadataIsNotValidJson() throws IOException {
        Path backupPath = backupDir.resolve("backup.tar");
        Files.writeString(metadataPathFor(backupPath), "not-valid-json");

        assertThat(handler.isBackupCompatible(backupPath)).isFalse();
    }

    @Test
    void isBackupNotCompatibleWhenFormatVersionFileMissing() throws IOException {
        BackupMetadataHandler brokenHandler = new BackupMetadataHandler(externalProcessRunner,
                backupDir.resolve("missing-version-file").toString(), "create-metadata-script", backupDir, SERVER_TYPE);
        Path backupPath = writeMetadata("backup.tar", FORMAT_VERSION, SERVER_TYPE);

        assertThat(brokenHandler.isBackupCompatible(backupPath)).isFalse();
    }

    @Test
    void deleteMetadataRemovesExistingFile() throws IOException {
        Path backupPath = writeMetadata("backup.tar", FORMAT_VERSION, SERVER_TYPE);

        handler.deleteMetadata(backupPath);

        assertThat(metadataPathFor(backupPath)).doesNotExist();
    }

    @Test
    void deleteMetadataIsNoopWhenFileAbsent() {
        Path backupPath = backupDir.resolve("backup.tar");

        handler.deleteMetadata(backupPath);

        assertThat(metadataPathFor(backupPath)).doesNotExist();
    }

    @Test
    void deleteMetadataIgnoresPathOutsideBackupDirectory() throws IOException {
        Path outsideBackup = Files.createTempFile("backup", ".tar");
        Path outsideMetadata = metadataPathFor(outsideBackup);
        Files.writeString(outsideMetadata, "irrelevant");

        handler.deleteMetadata(outsideBackup);

        assertThat(outsideMetadata).exists();
    }

    @Test
    void createMetadataDoesNotTouchMetadataWhenScriptSucceeds() throws Exception {
        Path backupPath = writeMetadata("backup.tar", FORMAT_VERSION, SERVER_TYPE);
        when(externalProcessRunner.execute(anyString(), anyString()))
                .thenReturn(new ExternalProcessRunner.ProcessResult("cmd", 0, List.of("ok")));

        handler.createMetadata(backupPath);

        assertThat(handler.isBackupCompatible(backupPath)).isTrue();
    }

    @Test
    void createMetadataDeletesStaleMetadataWhenProcessNotExecutable() throws Exception {
        Path backupPath = writeMetadata("backup.tar", FORMAT_VERSION, SERVER_TYPE);
        when(externalProcessRunner.execute(anyString(), anyString()))
                .thenThrow(new ProcessNotExecutableException(new IOException("boom")));

        handler.createMetadata(backupPath);

        assertThat(metadataPathFor(backupPath)).doesNotExist();
    }

    @Test
    void createMetadataDeletesStaleMetadataWhenProcessFailed() throws Exception {
        Path backupPath = writeMetadata("backup.tar", FORMAT_VERSION, SERVER_TYPE);
        when(externalProcessRunner.execute(anyString(), anyString()))
                .thenThrow(new ProcessFailedException("failed"));

        handler.createMetadata(backupPath);

        assertThat(metadataPathFor(backupPath)).doesNotExist();
    }

    @Test
    void createMetadataDeletesStaleMetadataWhenInterrupted() throws Exception {
        Path backupPath = writeMetadata("backup.tar", FORMAT_VERSION, SERVER_TYPE);
        when(externalProcessRunner.execute(anyString(), anyString()))
                .thenThrow(new InterruptedException("interrupted"));

        handler.createMetadata(backupPath);

        assertThat(metadataPathFor(backupPath)).doesNotExist();
    }

    @Test
    void createMetadataDoesNotRunScriptForPathOutsideBackupDirectory() {
        Path outsideBackup = backupDir.resolve("../outside.tar");

        handler.createMetadata(outsideBackup);

        verifyNoInteractions(externalProcessRunner);
    }

    private Path writeMetadata(String backupFileName, String version, String serverType) throws IOException {
        Path backupPath = backupDir.resolve(backupFileName);
        Files.writeString(metadataPathFor(backupPath), "{\"version\":\"%s\",\"server_type\":\"%s\"}".formatted(version, serverType));
        return backupPath;
    }

    private static Path metadataPathFor(Path backupPath) {
        return backupPath.resolveSibling(backupPath.getFileName() + ".metadata");
    }
}
