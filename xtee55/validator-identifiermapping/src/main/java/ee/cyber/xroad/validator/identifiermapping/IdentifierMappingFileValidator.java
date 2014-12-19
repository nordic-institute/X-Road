package ee.cyber.xroad.validator.identifiermapping;

import java.util.List;

import org.apache.commons.lang.StringUtils;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.identifier.ClientId;

public class IdentifierMappingFileValidator {
    public static final String INVALID_INSTANCE_IDENTIFIER =
            "IdentifiermappingInvalidSdsbInstance";
    public static final String INVALID_MEMBER_CLASSES =
            "IdentifiermappingInvalidMemberClasses";

    private byte[] fileContent;
    private String currentInstanceIdentifier;
    private List<String> allowedMemberClasses;
    private IdentifierMappingSchemaValidator schemaValidator;

    public IdentifierMappingFileValidator(
            IdentifierMappingSchemaValidator schemaValidator,
            byte[] fileContent,
            String currentInstanceIdentifier,
            List<String> allowedMemberClasses) {
        this.schemaValidator = schemaValidator;
        this.fileContent = fileContent;
        this.currentInstanceIdentifier = currentInstanceIdentifier;
        this.allowedMemberClasses = allowedMemberClasses;
    }

    public void validate() throws Exception {
        validateIntegrity();
        validateContent();
    }

    private void validateIntegrity() throws Exception {
        schemaValidator.validate(fileContent);
    }

    private void validateContent() throws Exception {
        MappingsType mappings =
                IdentifierMappingUnmarshaller.unmarshal(fileContent);

        mappings.getMapping().forEach(each -> {
            ClientId newId = each.getNewId();

            validateMemberClass(newId.getMemberClass());
            validateSdsbInstance(newId.getSdsbInstance());
        });
    }

    private void validateMemberClass(String memberClass) {
        if (allowedMemberClasses.contains(memberClass)) {
            return;
        }

        String allowedClassesAsString =
                StringUtils.join(allowedMemberClasses, ", ");

        String message = String.format(
                "Member class '%s' is not allowed in this "
                + "system, allowed ones are '%s'.",
                memberClass, allowedClassesAsString);

        throw new CodedException(INVALID_MEMBER_CLASSES, message);
    }

    private void validateSdsbInstance(String sdsbInstance) {
        if (StringUtils.equals(sdsbInstance, currentInstanceIdentifier)) {
            return;
        }

        String message = String.format(
                "Identifier mapping includes SDSB instance "
                + "'%s', but allowed one is '%s'.", sdsbInstance,
                currentInstanceIdentifier);

        throw new CodedException(INVALID_INSTANCE_IDENTIFIER, message);
    }
}
