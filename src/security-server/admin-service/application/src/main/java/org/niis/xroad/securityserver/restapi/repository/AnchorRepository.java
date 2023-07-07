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
package org.niis.xroad.securityserver.restapi.repository;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.globalconf.ConfigurationAnchorV2;
import ee.ria.xroad.common.util.AtomicSave;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Anchor repository
 */
@Slf4j
@Repository
public class AnchorRepository {
    private static final String CONFIGURATION_ANCHOR_FILENAME = SystemProperties.getConfigurationAnchorFile();

    /**
     * Read anchor file's content
     * @return
     * @throws NoSuchFileException if anchor file does not exist
     */
    public byte[] readAnchorFile() throws NoSuchFileException {
        Path path = Paths.get(CONFIGURATION_ANCHOR_FILENAME);
        try {
            return Files.readAllBytes(path);
        } catch (NoSuchFileException nsfe) {
            log.error("anchor file does not exist (" + path.toString() + ")");
            throw nsfe;
        } catch (IOException ioe) {
            log.error("can't read anchor file's content (" + path.toString() + ")");
            throw new RuntimeException(ioe);
        }
    }

    /**
     * Load anchor from file
     * @return
     */
    public ConfigurationAnchorV2 loadAnchorFromFile() {
        return new ConfigurationAnchorV2(CONFIGURATION_ANCHOR_FILENAME);
    }

    /**
     * Save anchor. The replacing of the old anchor file is done atomically.
     * @return
     * @throws IOException if atomic save fails
     */
    public void saveAndReplace(File anchorFile) throws IOException {
        try {
            AtomicSave.moveBetweenFilesystems(anchorFile.getAbsolutePath(), CONFIGURATION_ANCHOR_FILENAME);
        } catch (Exception e) {
            log.error("Saving anchor failed", e);
            throw e;
        }
    }
}
