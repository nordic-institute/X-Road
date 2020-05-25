/**
 * The MIT License
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
package org.niis.xroad.restapi.openapi;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.converter.BackupConverter;
import org.niis.xroad.restapi.dto.BackupFile;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.openapi.model.Backup;
import org.niis.xroad.restapi.openapi.model.TokensLoggedOut;
import org.niis.xroad.restapi.service.BackupFileNotFoundException;
import org.niis.xroad.restapi.service.BackupService;
import org.niis.xroad.restapi.service.InvalidBackupFileException;
import org.niis.xroad.restapi.service.InvalidFilenameException;
import org.niis.xroad.restapi.service.RestoreProcessFailedException;
import org.niis.xroad.restapi.service.RestoreService;
import org.niis.xroad.restapi.service.TokenService;
import org.niis.xroad.restapi.service.UnhandledWarningsException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Backups controller
 */
@Controller
@RequestMapping("/api")
@Slf4j
@PreAuthorize("denyAll")
public class BackupsApiController implements BackupsApi {
    public static final String GENERATE_BACKUP_INTERRUPTED = "generate_backup_interrupted";
    public static final String RESTORE_INTERRUPTED = "backup_restore_interrupted";

    private final BackupService backupService;
    private final RestoreService restoreService;
    private final BackupConverter backupConverter;
    private final TokenService tokenService;

    @Autowired
    public BackupsApiController(BackupService backupService,
            RestoreService restoreService, BackupConverter backupConverter,
            TokenService tokenService) {
        this.backupService = backupService;
        this.restoreService = restoreService;
        this.backupConverter = backupConverter;
        this.tokenService = tokenService;
    }

    @Override
    @PreAuthorize("hasAuthority('BACKUP_CONFIGURATION')")
    public ResponseEntity<List<Backup>> getBackups() {
        List<BackupFile> backupFiles = backupService.getBackupFiles();

        return new ResponseEntity<>(backupConverter.convert(backupFiles), HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('BACKUP_CONFIGURATION')")
    public ResponseEntity<Void> deleteBackup(String filename) {
        try {
            backupService.deleteBackup(filename);
        } catch (BackupFileNotFoundException e) {
            throw new ResourceNotFoundException(e);
        }

        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Override
    @PreAuthorize("hasAuthority('BACKUP_CONFIGURATION')")
    public ResponseEntity<Resource> downloadBackup(String filename) {
        byte[] backupFile = null;
        try {
            backupFile = backupService.readBackupFile(filename);
        } catch (BackupFileNotFoundException e) {
            throw new ResourceNotFoundException(e);
        }
        return ApiUtil.createAttachmentResourceResponse(backupFile, filename);
    }

    @Override
    @PreAuthorize("hasAuthority('BACKUP_CONFIGURATION')")
    public ResponseEntity<Backup> addBackup() {
        try {
            BackupFile backupFile = backupService.generateBackup();
            return new ResponseEntity<>(backupConverter.convert(backupFile), HttpStatus.CREATED);
        } catch (InterruptedException e) {
            throw new InternalServerErrorException(new ErrorDeviation(GENERATE_BACKUP_INTERRUPTED));
        }
    }

    @Override
    @PreAuthorize("hasAuthority('BACKUP_CONFIGURATION')")
    public ResponseEntity<Backup> uploadBackup(Boolean ignoreWarnings, MultipartFile file) {
        try {
            BackupFile backupFile = backupService.uploadBackup(ignoreWarnings, file.getOriginalFilename(),
                    file.getBytes());
            return new ResponseEntity<>(backupConverter.convert(backupFile), HttpStatus.CREATED);
        } catch (InvalidFilenameException | UnhandledWarningsException | InvalidBackupFileException e) {
            throw new BadRequestException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    @PreAuthorize("hasAuthority('RESTORE_CONFIGURATION')")
    public synchronized ResponseEntity<TokensLoggedOut> restoreBackup(String filename) {
        boolean hasHardwareTokens = tokenService.hasHardwareTokens();
        // If hardware tokens exist prior to the restore -> they will be logged out by the restore script
        TokensLoggedOut tokensLoggedOut = new TokensLoggedOut().hsmTokensLoggedOut(hasHardwareTokens);
        try {
            restoreService.restoreFromBackup(filename);
        } catch (BackupFileNotFoundException e) {
            throw new BadRequestException(e);
        } catch (InterruptedException e) {
            throw new InternalServerErrorException(new ErrorDeviation(RESTORE_INTERRUPTED));
        } catch (RestoreProcessFailedException e) {
            throw new InternalServerErrorException(e);
        }
        return new ResponseEntity<>(tokensLoggedOut, HttpStatus.OK);
    }
}
