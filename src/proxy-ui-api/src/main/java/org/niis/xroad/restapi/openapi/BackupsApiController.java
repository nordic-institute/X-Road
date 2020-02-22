/**
 * The MIT License
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
import org.niis.xroad.restapi.openapi.model.Backup;
import org.niis.xroad.restapi.service.BackupFileNotFoundException;
import org.niis.xroad.restapi.service.BackupsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * Backups controller
 */
@Controller
@RequestMapping("/api")
@Slf4j
@PreAuthorize("denyAll")
public class BackupsApiController implements BackupsApi {

    private final BackupsService backupsService;
    @Autowired
    public BackupsApiController(BackupsService backupsService) {
        this.backupsService = backupsService;
    }

    @Override
    @PreAuthorize("hasAuthority('BACKUP_CONFIGURATION')")
    public ResponseEntity<List<Backup>> getBackups() {
        List<Backup> backups = backupsService.getBackupFiles();

        return new ResponseEntity<>(backups, HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('BACKUP_CONFIGURATION')")
    public ResponseEntity<Void> deleteBackup(String filename) {
        try {
            backupsService.deleteBackup(filename);
        } catch (BackupFileNotFoundException e) {
            throw new BadRequestException(e);
        }

        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

}
