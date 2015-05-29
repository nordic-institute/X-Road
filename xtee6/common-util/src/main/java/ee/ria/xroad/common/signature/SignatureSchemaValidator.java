package ee.ria.xroad.common.signature;

import javax.xml.transform.Source;
import javax.xml.validation.Schema;

import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.common.util.SchemaValidator;

/**
 * Validates signature XML according to schema.
 */
public class SignatureSchemaValidator extends SchemaValidator {

    private static final String FILE = "xades-schema.xsd";

    private static Schema schema;

    static {
        schema = createSchema(FILE);
    }

    /**
     * Validates the provided XML signature source.
     * @param source source of the XML signature to be validated
     * @throws Exception if validation is unsuccessful
     */
    public static void validate(Source source) throws Exception {
        validate(schema, source, ErrorCodes.X_MALFORMED_SIGNATURE);
    }
}
