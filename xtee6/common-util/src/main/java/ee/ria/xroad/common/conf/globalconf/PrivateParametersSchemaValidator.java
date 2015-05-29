package ee.ria.xroad.common.conf.globalconf;

import java.io.StringReader;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;

import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.common.util.SchemaValidator;

/**
 * Schema validator of private parameters.
 */
public class PrivateParametersSchemaValidator extends SchemaValidator {
    private static Schema schema;

    static {
        schema = createSchema("globalconf/internal-conf.xsd");
    }

    /**
     * Validates the input XML as string against the schema.
     * @param xml the input XML as string
     * @throws Exception if validation fails
     */
    public static void validate(String xml) throws Exception {
        validate(new StreamSource(new StringReader(xml)));
    }

    /**
     * Validates the input source against the schema.
     * @param source the input source
     * @throws Exception if validation fails
     */
    public static void validate(Source source) throws Exception {
        validate(schema, source, ErrorCodes.X_MALFORMED_GLOBALCONF);
    }
}
