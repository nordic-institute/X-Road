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
package ee.ria.xroad.commonui;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.common.conf.globalconf.ConfigurationConstants;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Encapsulates optional parts configuration of central server.
 */
@Slf4j
public class OptionalPartsConf {
    private static final String KEY_PART_FILE_NAME;
    private static final String KEY_CONTENT_IDENTIFIER;
    private static final String KEY_VALIDATION_PROGRAM;

    private static final List<String> RESERVED_FILE_NAMES;
    private static final List<String> RESERVED_CONTENT_IDENTIFIERS;

    static {
        KEY_PART_FILE_NAME = "file-name";
        KEY_CONTENT_IDENTIFIER = "content-identifier";
        KEY_VALIDATION_PROGRAM = "validation-program";

        RESERVED_FILE_NAMES = Arrays.asList(
            ConfigurationConstants.FILE_NAME_PRIVATE_PARAMETERS,
            ConfigurationConstants.FILE_NAME_SHARED_PARAMETERS);
        RESERVED_CONTENT_IDENTIFIERS = Arrays.asList(
            ConfigurationConstants.CONTENT_ID_PRIVATE_PARAMETERS,
            ConfigurationConstants.CONTENT_ID_SHARED_PARAMETERS);
    }

    private final Map<String, String> partFileNameToValidationProgram =
            new HashMap<>();
    private final Map<String, String> partFileNameToContentIdentifier =
            new HashMap<>();

    @Getter
    private final List<OptionalConfPart> allParts = new ArrayList<>();

    @Getter
    private final List<String> errors = new ArrayList<>();

    private final Set<String> existingPartFileNames = new HashSet<>();

    /**
     * Creates optional parts configuration.
     *
     * @param confDir - directory, where optional part files are in.
     * @throws IOException - when optional parts directory cannot be read.
     */
    public OptionalPartsConf(String confDir) throws IOException {
        File optionalPartsDir = new File(confDir);

        final String optionalPartsPath = optionalPartsDir.getAbsolutePath();

        if (!optionalPartsDir.isDirectory()) {
            log.warn("Optional configuration parts directory '{}' "
                            + "either does not exist or is regular file",
                    optionalPartsPath);
            return;
        }

        log.debug("Getting optional conf parts from directory '{}'", confDir);

        File[] optionalPartFiles =
                optionalPartsDir.listFiles(getIniFileFilter());

        if (optionalPartFiles == null) {
            log.warn("Optional part files list in directory '{}' "
                    + "cannot be fetched.", optionalPartsPath);
            return;
        }

        List<File> files = Arrays.asList(optionalPartFiles);

        files.forEach(this::processFile);
    }

    private FileFilter getIniFileFilter() {
        return new RegexFileFilter("^.+\\.ini$");
    }

    /**
     * Returns absolute path to validation program according to path file name.
     *
     * @param partFile - Simple name of the part file.
     * @return - absolute path to validation program.
     */
    public String getValidationProgram(String partFile) {
        String validationProgram =
                partFileNameToValidationProgram.get(partFile);

        log.debug("Validation program for part file '{}': '{}'",
                partFile, validationProgram);

        return validationProgram;
    }

    /**
     * Returns content identifier respective to path file name.
     *
     * @param partFile - simple name of the part file.
     * @return - content identifier respective to part file.
     */
    public String getContentIdentifier(String partFile) {
        String contentIdentifier =
                partFileNameToContentIdentifier.get(partFile);

        log.debug("Content identifier for part file '{}': '{}'",
                partFile, contentIdentifier);

        return contentIdentifier;
    }

    @SneakyThrows
    private void processFile(File confFile) {
        try {
            Properties props = new Properties();
            props.load(new FileInputStream(confFile));

            String partFileName = props.getProperty(KEY_PART_FILE_NAME);
            String contentId = props.getProperty(KEY_CONTENT_IDENTIFIER);

            if (!isFileContentWellFormed(partFileName, contentId)) {
                log.warn("Optional part configuration file '{}' is malformed, "
                        + "please inspect it for correctness.",
                        confFile.getAbsolutePath());
                return;
            }

            String validationProgram = props.getProperty(KEY_VALIDATION_PROGRAM);

            validatePartFileName(partFileName);

            validateContentIdentifier(contentId);


            partFileNameToValidationProgram.put(partFileName, validationProgram);
            partFileNameToContentIdentifier.put(partFileName, contentId);

            allParts.add(new OptionalConfPart(partFileName, contentId));
        } catch (IOException e) {
            log.error("Loading optional parts from file '"
                    + confFile.getAbsolutePath() + "' failed: {}",
                    e.getMessage(), e); // throwable as last object param should work as of SLF4J 1.6.0

            errors.add(e.getMessage());
        }
    }

    private boolean isFileContentWellFormed(
            String partFile, String contentId) {
        return !(StringUtils.isBlank(partFile)
                || StringUtils.isBlank(contentId));

    }

    private void validatePartFileName(String partFileName) {
        if (RESERVED_FILE_NAMES.contains(partFileName)) {
            throw new CodedException(
                    ErrorCodes.X_MALFORMED_OPTIONAL_PARTS_CONF,
                    "Optional parts configuration contains reserved filename'"
                            + partFileName + "'.");
        }

        if (!existingPartFileNames.add(partFileName)) {
            throw new CodedException(
                    ErrorCodes.X_MALFORMED_OPTIONAL_PARTS_CONF,
                    "Part file name'" + partFileName + "' occurs more than "
                            + "once in optional parts configuration. "
                            + "Part file names must be unique.");
        }
    }

    private void validateContentIdentifier(String contentId) {
        if (RESERVED_CONTENT_IDENTIFIERS.contains(contentId)) {
            throw new CodedException(
                    ErrorCodes.X_MALFORMED_OPTIONAL_PARTS_CONF,
                    "Optional parts configuration contains reserved content "
                            + "identifier'" + contentId + "'.");
        }
    }
}
