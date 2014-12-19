package ee.cyber.xroad.validator.identifiermapping;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.xml.sax.SAXException;

import ee.cyber.sdsb.common.ExpectedCodedException;

public class IdentifierMappingFileValidatorBehavior {
    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    @Before
    public void setUp() {
        System.setProperty(
                IdentifierMappingSchemaValidator.PROP_SCHEMA_FILE,
                "doc/identifier-mapping.xsd");
    }

    @Test
    public void shouldValidateIdentifierMappingSuccessfully() throws Exception {
        // Given
        byte [] fileContent = getFileContent("identifiermapping.xml");
        String currentInstanceIdentifier = "EE";
        List<String> allowedMemberClasses = Arrays.asList("GOV");

        IdentifierMappingFileValidator validator =
                new IdentifierMappingFileValidator(
                        getSchemaValidator(),
                        fileContent,
                        currentInstanceIdentifier,
                        allowedMemberClasses);

        // When/then
        validator.validate();
    }

    @Test
    public void shouldRaiseErrorWhenInvalidXml() throws Exception {
        // Given
        thrown.expectError(
                IdentifierMappingSchemaValidator.ERROR_CODE_MALFORMED);

        byte [] fileContent = getFileContent("identifiermapping-INVALID.xml");
        String currentInstanceIdentifier = "EE";
        List<String> allowedMemberClasses = Arrays.asList("GOV");

        IdentifierMappingFileValidator validator =
                new IdentifierMappingFileValidator(
                        getSchemaValidator(),
                        fileContent,
                        currentInstanceIdentifier,
                        allowedMemberClasses);

        // When/then
        validator.validate();
    }

    @Test
    public void shouldRaiseErrorWhenInvalidMemberClasses() throws Exception {
        // Given
        thrown.expectError(
                IdentifierMappingFileValidator.INVALID_MEMBER_CLASSES);

        byte [] fileContent =
                getFileContent("identifiermapping-MORE_MEMBER_CLASSES.xml");
        String currentInstanceIdentifier = "EE";
        List<String> allowedMemberClasses = Arrays.asList("GOV");

        IdentifierMappingFileValidator validator =
                new IdentifierMappingFileValidator(
                        getSchemaValidator(),
                        fileContent,
                        currentInstanceIdentifier,
                        allowedMemberClasses);

        // When/then
        validator.validate();
    }

    @Test
    public void shouldRaiseErrorWhenInvalidInstanceCode() throws Exception {
        // Given
        thrown.expectError(
                IdentifierMappingFileValidator.INVALID_INSTANCE_IDENTIFIER);

        byte [] fileContent =
                getFileContent("identifiermapping-WRONG_INSTANCE_CODE.xml");
        String currentInstanceIdentifier = "EE";
        List<String> allowedMemberClasses = Arrays.asList("GOV");

        IdentifierMappingFileValidator validator =
                new IdentifierMappingFileValidator(
                        getSchemaValidator(),
                        fileContent,
                        currentInstanceIdentifier,
                        allowedMemberClasses);

        // When/then
        validator.validate();
    }

    private byte[] getFileContent(String fileName) throws IOException {
        return IOUtils.toByteArray(
                new FileInputStream(new File("src/test/resources/" + fileName)));
    }

    private IdentifierMappingSchemaValidator getSchemaValidator() {
        SchemaFactory factory = SchemaFactory
                .newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        try {
            File schemaLocation = new File("doc/identifier-mapping.xsd");
            Schema schema = factory.newSchema(schemaLocation);

            return new IdentifierMappingSchemaValidator(schema);
        } catch (SAXException e) {
            throw new RuntimeException("Unable to create schema validator", e);
        }
    }
}
