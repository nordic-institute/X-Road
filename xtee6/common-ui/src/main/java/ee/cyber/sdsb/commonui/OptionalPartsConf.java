package ee.cyber.sdsb.commonui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.ErrorCodes;
import ee.cyber.sdsb.common.conf.globalconf.PrivateParameters;
import ee.cyber.sdsb.common.conf.globalconf.SharedParameters;

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
                PrivateParameters.FILE_NAME_PRIVATE_PARAMETERS,
                SharedParameters.FILE_NAME_SHARED_PARAMETERS);
        RESERVED_CONTENT_IDENTIFIERS = Arrays.asList(
                PrivateParameters.CONTENT_ID_PRIVATE_PARAMETERS,
                SharedParameters.CONTENT_ID_SHARED_PARAMETERS);
    }

    private final HierarchicalINIConfiguration conf;

    private final Map<String, String> partFileNameToValidationProgram =
            new HashMap<>();
    private final Map<String, String> partFileNameToContentIdentifier =
            new HashMap<>();

    @Getter
    private final List<OptionalConfPart> allParts = new ArrayList<>();

    private final Set<String> existingPartFileNames = new HashSet<>();

    public OptionalPartsConf(String confFile) throws IOException {
        try {
            this.conf = new HierarchicalINIConfiguration(confFile);

            this.conf.getSections().forEach(this::processSection);
        } catch (ConfigurationException e) {
            log.error("Loading optional parts configuration failed", e);

            throw new IOException(
                    "Could not load optional parts configuration: ", e);
        }
    }

    public String getValidationProgram(String partFile) {
        String validationProgram =
                partFileNameToValidationProgram.get(partFile);

        log.debug("Validation program for part file '{}': '{}'",
                partFile, validationProgram);

        return validationProgram;
    }

    public String getContentIdentifier(String partFile) {
        String contentIdentifier =
                partFileNameToContentIdentifier.get(partFile);

        log.debug("Content identifier for part file '{}': '{}'",
                partFile, contentIdentifier);

        return contentIdentifier;
    }

    private void processSection(String sectionName) {
        SubnodeConfiguration section = conf.getSection(sectionName);

        String partFileName = section.getString(KEY_PART_FILE_NAME);
        String contentId = section.getString(KEY_CONTENT_IDENTIFIER);
        String validationProgram = section.getString(KEY_VALIDATION_PROGRAM);

        validatePartFileName(partFileName);

        validateContentIdentifier(contentId);


        partFileNameToValidationProgram.put(partFileName, validationProgram);
        partFileNameToContentIdentifier.put(partFileName, contentId);

        allParts.add(new OptionalConfPart(partFileName, contentId));
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
