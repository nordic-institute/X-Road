package ee.cyber.sdsb.centerui;

import java.io.StringReader;

import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;

import ee.cyber.sdsb.common.util.SchemaValidator;

public class IdentifierMappingSchemaValidator extends SchemaValidator {
    private static Schema schema;

    static {
        schema = createSchema("identifier-mapping.xsd");
    }

    public static void validate(String xml) throws Exception {
        validate(
                schema,
                new StreamSource(new StringReader(xml)),
                "MalformedIdentifierMapping");
    }
}
