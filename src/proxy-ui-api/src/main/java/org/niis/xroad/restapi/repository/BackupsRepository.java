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
package org.niis.xroad.restapi.repository;

import ee.ria.xroad.common.SystemProperties;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Backups repository
 */
@Slf4j
@Repository
public class BackupsRepository {

    private static final String CONFIGURATION_BACKUP_PATH = SystemProperties.getConfBackupPath();
    private static final String BACKUP_FILE_EXTENSION = ".tar";
    // Set maximum number of levels of directories to visit, subdirectories are excluded
    private static final int DIR_MAX_DEPTH = 1;

    /**
     * Read backup files from configuration backup path
     * @return
     */
    public List<File> getBackupFiles() {
        try (Stream<Path> walk = Files.walk(Paths.get(CONFIGURATION_BACKUP_PATH), DIR_MAX_DEPTH)) {
            List<File> files = walk.map(x -> x.toString())
                    .filter(f -> f.endsWith(BACKUP_FILE_EXTENSION))
                    .map(p -> new File(p)).collect(Collectors.toList());

            return files;
        } catch (IOException ioe) {
            log.error("can't read backup files from configuration path (" + CONFIGURATION_BACKUP_PATH + ")");
            throw new RuntimeException(ioe);
        }
    }

    /**
     * Get the creation date/time of a backup file
     * @param filename
     * @return
     */
    public Date getCreatedAt(String filename) {
        Path path = Paths.get(CONFIGURATION_BACKUP_PATH + "/" + filename);
        try {
            FileTime creationTime = (FileTime) Files.getAttribute(path, "creationTime");
            return new Date(creationTime.toMillis());
        } catch (IOException ioe) {
            log.warn("can't read backup file's creation time (" + path.toString() + ")");
            throw new RuntimeException(ioe);
        }
    }
}
