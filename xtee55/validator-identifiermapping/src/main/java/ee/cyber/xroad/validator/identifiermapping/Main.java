package ee.cyber.xroad.validator.identifiermapping;

import java.io.IOException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

import ee.cyber.sdsb.common.CodedException;

import static ee.cyber.sdsb.common.ErrorCodes.translateException;
import static ee.cyber.xroad.validator.identifiermapping.IdentifierMappingSchemaValidator.createSchema;

public class Main {

    /**
     * Starting point of running identifier mapping validator. Errors and
     * warnings are written into standard error. If validation is unsuccessful,
     * it returns exit code other than 0.
     *
     * @param args
     *            - takes identifier mapping file content as the only argument.
     */
    public static void main(String[] args) {
        setEncoding();

        try {
            getValidator(readInputFile()).validate();

            System.out.println("Identifier mapping validated successfully.");
        } catch (Exception e) {
            handleError(translateException(e));
        }
    }

    private static byte[] readInputFile() throws IOException {
        return IOUtils.toByteArray(System.in);
    }

    private static IdentifierMappingFileValidator getValidator(
            byte[] mappingFileContent) throws Exception {
        try (Conf conf = new Conf()) {
            IdentifierMappingFileValidator validator =
                    new IdentifierMappingFileValidator(
                            new IdentifierMappingSchemaValidator(createSchema()),
                            mappingFileContent,
                            conf.getInstanceIdentifier(),
                            conf.getAllowedMemberClasses());

            return validator;
        }
    }

    private static void handleError(CodedException e) {
        System.err.println("Validation of identifier mapping failed.");

        System.err.println("Fault string: " + e.getFaultString());
        System.err.println("Fault code: " + e.getFaultCode());
        System.err.println("Fault detail:\n" + reduceFaultDetail(e));

        System.exit(1);
    }

    /**
     * Reduces fault detail to preserve only first line (message part) of it.
     */
    private static String reduceFaultDetail(CodedException e) {
        String faultDetail = e.getFaultDetail();

        if (StringUtils.isBlank(faultDetail)) {
            return "";
        }

        String[] faultDetailLines = faultDetail.split("\n");

        StringBuilder sb = new StringBuilder();

        for (String each : faultDetailLines) {
            if (StringUtils.isBlank(each) || isStackTraceLine(each)) {
                break;
            }

            sb.append(each).append("\n");
        }

        return sb.toString();
    }

    private static boolean isStackTraceLine(String line) {
        // TODO: This is quite naive check, provide more bulletproof.
        return Character.isWhitespace(line.charAt(0))
                && line.trim().startsWith("at");
    }

    private static void setEncoding() {
        System.setProperty("file.encoding", "UTF-8");
    }
}
