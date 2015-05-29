package ee.cyber.xroad.validator.identifiermapping;

import java.util.List;

import org.apache.commons.lang.StringUtils;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.identifier.ClientId;

class IdentifierMappingFileValidator {

    public static final String INVALID_INSTANCE_IDENTIFIER =
            "IdentifiermappingInvalidXRoadInstance";
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
            validateXRoadInstance(newId.getXRoadInstance());
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

    private void validateXRoadInstance(String xRoadInstance) {
        if (StringUtils.equals(xRoadInstance, currentInstanceIdentifier)) {
            return;
        }

        String message = String.format(
                "Identifier mapping includes X-Road 6.0 instance "
                + "'%s', but allowed one is '%s'.", xRoadInstance,
                currentInstanceIdentifier);

        throw new CodedException(INVALID_INSTANCE_IDENTIFIER, message);
    }
}
