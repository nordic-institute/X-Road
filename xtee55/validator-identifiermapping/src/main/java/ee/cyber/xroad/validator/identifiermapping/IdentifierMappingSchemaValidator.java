package ee.cyber.xroad.validator.identifiermapping;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;

import ee.ria.xroad.common.util.SchemaValidator;

class IdentifierMappingSchemaValidator extends SchemaValidator {
    public static final String PROP_SCHEMA_FILE =
            "ee.cyber.xroad.validator.identifiermapping.schemaFile";

    public static final String ERROR_CODE_MALFORMED =
            "MalformedIdentifierMapping";

    private Schema schema;

    public IdentifierMappingSchemaValidator(Schema schema) {
        this.schema = schema;
    }

    public void validate(byte[] fileContent) throws Exception {
        try (InputStream is = new ByteArrayInputStream(fileContent)) {
            Source xmlSource = new StreamSource(is);
            validate(schema, xmlSource, ERROR_CODE_MALFORMED);
        }
    }

    static Schema createSchema() {
        return createSchema("identifier-mapping.xsd");
    }
}
