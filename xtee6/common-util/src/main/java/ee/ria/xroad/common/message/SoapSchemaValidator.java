package ee.ria.xroad.common.message;

import javax.xml.transform.Source;
import javax.xml.validation.Schema;

import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.common.util.SchemaValidator;

/**
 * Validates SOAP messages according to schema.
 */
public class SoapSchemaValidator extends SchemaValidator {

    private static final String FILE = "soap-schema.xsd";

    private static Schema schema;

    static {
        schema = createSchema(FILE);
    }

    /**
     * Validates the provided SOAP message source.
     * @param source source of the SOAP message to be validated
     * @throws Exception if validation is unsuccessful
     */
    public static void validate(Source source) throws Exception {
        validate(schema, source, ErrorCodes.X_MALFORMED_SOAP);
    }
}
