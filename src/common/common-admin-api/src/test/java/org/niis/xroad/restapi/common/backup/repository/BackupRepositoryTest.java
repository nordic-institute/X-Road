package org.niis.xroad.restapi.common.backup.repository;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.exception.ServiceException;
import org.niis.xroad.restapi.common.backup.service.BackupValidator;

import java.io.IOException;
import java.nio.file.Path;

import static ee.ria.xroad.common.SystemProperties.CONF_BACKUP_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class BackupRepositoryTest {

    private static final String BACKUP_FILE_NAME = "file.zip";

    @Mock
    private BackupValidator backupValidator;

    @TempDir
    Path backupDir;

    private BackupRepository repository;

    @BeforeEach
    void beforeEach() {
        System.setProperty(CONF_BACKUP_PATH, backupDir.toAbsolutePath().toString());
        repository = new BackupRepository(backupValidator);
    }

    @Test
    void getAbsoluteBackupFilePath() {
       assertThat(repository.getAbsoluteBackupFilePath(BACKUP_FILE_NAME)).isEqualTo(backupDir.resolve(BACKUP_FILE_NAME));
    }

    @Test
    void fileExists() throws IOException {
        assertThat(repository.fileExists(BACKUP_FILE_NAME)).isFalse();
        backupDir.resolve(BACKUP_FILE_NAME).toFile().createNewFile();
        assertThat(repository.fileExists(BACKUP_FILE_NAME)).isTrue();
    }
    @Test
    void fileExistsShouldFailOnRelativePathName() throws IOException {
        assertThatThrownBy(() -> repository.fileExists("../secret/folder/file.txt"))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Invalid filename");
    }

}
