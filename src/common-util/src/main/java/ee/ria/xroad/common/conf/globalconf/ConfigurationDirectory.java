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
package ee.ria.xroad.common.conf.globalconf;

import ee.ria.xroad.common.util.AtomicSave;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Configuration directory interface.
 */
public interface ConfigurationDirectory {
    String FILES = "files";
    String METADATA_SUFFIX = ".metadata";
    String INSTANCE_IDENTIFIER_FILE = "instance-identifier";

    // Logger specified here because annotation does not work in interface.
    Logger LOG = LoggerFactory.getLogger(ConfigurationDirectory.class);

    /**
     * Saves the file to disk along with corresponding expiration date file.
     *
     * @param fileName the name of the file to save
     * @param content the content of the file
     * @param expirationDate the file expiration date
     * @throws Exception if an error occurs
     */
    static void save(Path fileName, byte[] content, ConfigurationPartMetadata expirationDate) throws Exception {
        if (fileName == null) {
            return;
        }

        Path parent = fileName.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        LOG.info("Saving content to file {}", fileName);

        // Save the content to disk.
        AtomicSave.execute(fileName.toString(), "conf", content, StandardCopyOption.ATOMIC_MOVE);

        // Save the content metadata date to disk.
        saveMetadata(fileName, expirationDate);
    }

    /**
     * Saves the expiration date for the given file.
     *
     * @param fileName the file
     * @param metadata the metadata
     * @throws Exception if an error occurs
     */
    static void saveMetadata(Path fileName, ConfigurationPartMetadata metadata) throws Exception {
        AtomicSave.execute(fileName.toString() + METADATA_SUFFIX, "expires", metadata.toByteArray(),
                StandardCopyOption.ATOMIC_MOVE);
    }

    /**
     * Saves the instance identifier of this security server to file.
     *
     * @param confPath path to the configuration
     * @param instanceIdentifier the instance identifier
     * @throws Exception if saving instance identifier fails
     */
    static void saveInstanceIdentifier(String confPath, String instanceIdentifier) throws Exception {
        Path file = Paths.get(confPath, INSTANCE_IDENTIFIER_FILE);

        LOG.trace("Saving instance identifier to {}", file);

        AtomicSave.execute(file.toString(), "inst", instanceIdentifier.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Deletes the file and accompanying expire date.
     *
     * @param fileName the file name
     */
    static void delete(String fileName) {
        File file = new File(fileName);

        if (!file.delete()) {
            LOG.error("Failed to delete file {}", file);
        }

        File metadataFile = new File(fileName + METADATA_SUFFIX);

        if (!metadataFile.delete()) {
            LOG.error("Failed to delete file {}", metadataFile);
        }

        File directory = file.getParentFile();

        if (directory.isDirectory()) {
            directory.delete(); // No need to check for return value.
        }
    }

    /**
     * Applies the given function to all files belonging to the configuration directory.
     *
     * @param consumer the function instance that should be applied to all files belonging to the configuration
     * directory.
     * @throws Exception if an error occurs
     */
    void eachFile(FileConsumer consumer) throws Exception;
}
