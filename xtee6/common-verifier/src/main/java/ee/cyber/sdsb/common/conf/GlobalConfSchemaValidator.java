package ee.cyber.sdsb.common.conf;

import java.io.StringReader;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;

import ee.cyber.sdsb.common.ErrorCodes;
import ee.cyber.sdsb.common.util.SchemaValidator;

public class GlobalConfSchemaValidator extends SchemaValidator {
    private static Schema schema;

    static {
        schema = createSchema("globalconf.xsd");
    }

    public static void validate(String xml) throws Exception {
        validate(new StreamSource(new StringReader(xml)));
    }

    public static void validate(Source source) throws Exception {
        validate(schema, source, ErrorCodes.X_MALFORMED_GLOBALCONF);
    }
}
