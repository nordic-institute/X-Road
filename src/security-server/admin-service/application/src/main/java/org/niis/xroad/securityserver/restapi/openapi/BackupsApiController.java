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
package org.niis.xroad.securityserver.restapi.openapi;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.backupmanager.proto.BackupInfo;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.common.exception.InternalServerErrorException;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.config.audit.RestApiAuditEvent;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.niis.xroad.restapi.service.UnhandledWarningsException;
import org.niis.xroad.securityserver.restapi.converter.BackupConverter;
import org.niis.xroad.securityserver.restapi.openapi.model.BackupDto;
import org.niis.xroad.securityserver.restapi.openapi.model.BackupExtDto;
import org.niis.xroad.securityserver.restapi.openapi.model.TokensLoggedOutDto;
import org.niis.xroad.securityserver.restapi.service.SecurityServerBackupService;
import org.niis.xroad.securityserver.restapi.service.TokenService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Set;

import static java.lang.Boolean.TRUE;
import static org.niis.xroad.common.core.exception.ErrorCodes.BACKUP_GENERATION_INTERRUPTED;
import static org.niis.xroad.common.core.exception.ErrorCodes.BACKUP_RESTORATION_INTERRUPTED;

/**
 * Backups controller
 */
@Controller
@RequestMapping(ControllerUtil.API_V1_PREFIX)
@Slf4j
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class BackupsApiController implements BackupsApi {
    private final BackupConverter backupConverter;
    private final SecurityServerBackupService backupService;
    private final TokenService tokenService;

    @Override
    @PreAuthorize("hasAuthority('BACKUP_CONFIGURATION')")
    public ResponseEntity<Set<BackupDto>> getBackups() {
        Collection<BackupInfo> backupFiles = backupService.listBackups();
        return new ResponseEntity<>(backupConverter.convert(backupFiles), HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('BACKUP_CONFIGURATION')")
    @AuditEventMethod(event = RestApiAuditEvent.DELETE_BACKUP)
    public ResponseEntity<Void> deleteBackup(String filename) {
        backupService.deleteBackup(filename);

        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Override
    @PreAuthorize("hasAuthority('BACKUP_CONFIGURATION')")
    public ResponseEntity<Resource> downloadBackup(String filename) {
        byte[] backupFile = backupService.readBackup(filename);
        return ControllerUtil.createAttachmentResourceResponse(backupFile, filename);
    }

    @Override
    @PreAuthorize("hasAuthority('BACKUP_CONFIGURATION')")
    @AuditEventMethod(event = RestApiAuditEvent.BACKUP)
    public ResponseEntity<BackupDto> addBackup() {
        try {
            BackupInfo backupFile = backupService.generateBackup();
            return new ResponseEntity<>(backupConverter.convert(backupFile), HttpStatus.CREATED);
        } catch (NotFoundException e) {
            throw new InternalServerErrorException(e);
        }
    }

    @Override
    @PreAuthorize("hasAuthority('BACKUP_CONFIGURATION')")
    @AuditEventMethod(event = RestApiAuditEvent.BACKUP)
    public ResponseEntity<BackupExtDto> addBackupExt() {
        BackupInfo backupFile = backupService.generateBackup();
        BackupExtDto backupExt = new BackupExtDto();
        backupExt.setFilename(backupFile.name());
        backupExt.setCreatedAt(backupFile.createdAt().atOffset(ZoneOffset.UTC));
        backupExt.setLocalConfPresent((new File("/etc/xroad/services/local.conf")).exists());
        return new ResponseEntity<>(backupExt, HttpStatus.CREATED);
    }

    @Override
    @PreAuthorize("hasAuthority('BACKUP_CONFIGURATION')")
    @AuditEventMethod(event = RestApiAuditEvent.UPLOAD_BACKUP)
    public ResponseEntity<BackupDto> uploadBackup(Boolean ignoreWarnings, MultipartFile file) {
        try {
            BackupInfo backupFile = backupService.uploadBackup(file.getOriginalFilename(), file.getBytes(),
                    TRUE.equals(ignoreWarnings));
            return new ResponseEntity<>(backupConverter.convert(backupFile), HttpStatus.CREATED);
        } catch (UnhandledWarningsException e) {
            throw new BadRequestException(e);
        } catch (IOException e) {
            throw new InternalServerErrorException(e);
        }
    }

    @Override
    @PreAuthorize("hasAuthority('RESTORE_CONFIGURATION')")
    @AuditEventMethod(event = RestApiAuditEvent.RESTORE_BACKUP)
    public synchronized ResponseEntity<TokensLoggedOutDto> restoreBackup(String filename) {
        boolean hasHardwareTokens = tokenService.hasHardwareTokens();
        // If hardware tokens exist prior to the restore -> they will be logged out by the restore script
        TokensLoggedOutDto tokensLoggedOut = new TokensLoggedOutDto().hsmTokensLoggedOut(hasHardwareTokens);
        try {
            backupService.restoreFromBackup(filename);
        } catch (NotFoundException e) {
            throw new InternalServerErrorException(e);
        }
        return new ResponseEntity<>(tokensLoggedOut, HttpStatus.OK);
    }

}
