package ee.cyber.sdsb.common.message;

import javax.xml.transform.Source;
import javax.xml.validation.Schema;

import ee.cyber.sdsb.common.ErrorCodes;
import ee.cyber.sdsb.common.util.SchemaValidator;

public class SoapSchemaValidator extends SchemaValidator {

    private static final String FILE = "soap-schema.xsd";

    private static Schema schema;

    static {
        schema = createSchema(FILE);
    }

    public static void validate(Source source) throws Exception {
        validate(schema, source, ErrorCodes.X_MALFORMED_SOAP);
    }
}
