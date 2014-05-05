package ee.cyber.sdsb.proxy.conf;

import javax.xml.transform.Source;
import javax.xml.validation.Schema;

import ee.cyber.sdsb.common.ErrorCodes;
import ee.cyber.sdsb.common.util.SchemaValidator;

public class ServerConfSchemaValidator extends SchemaValidator {

    private static Schema schema;

    static {
        schema = createSchema("serverconf.xsd");
    }

    public static void validate(Source source) throws Exception {
        validate(schema, source, ErrorCodes.X_MALFORMED_SERVERCONF);
    }
}
