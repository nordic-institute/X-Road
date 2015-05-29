package ee.ria.xroad_legacy.common.signature;

import javax.xml.transform.Source;
import javax.xml.validation.Schema;

import ee.ria.xroad_legacy.common.ErrorCodes;
import ee.ria.xroad_legacy.common.util.SchemaValidator;

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
