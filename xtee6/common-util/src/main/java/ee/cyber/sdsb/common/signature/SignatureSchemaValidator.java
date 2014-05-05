package ee.cyber.sdsb.common.signature;

import javax.xml.transform.Source;
import javax.xml.validation.Schema;

import ee.cyber.sdsb.common.ErrorCodes;
import ee.cyber.sdsb.common.util.SchemaValidator;

public class SignatureSchemaValidator extends SchemaValidator {

    private static final String FILE = "xades-schema.xsd";

    private static Schema schema;

    static {
        schema = createSchema(FILE);
    }

    public static void validate(Source source) throws Exception {
        validate(schema, source, ErrorCodes.X_MALFORMED_SIGNATURE);
    }
}
