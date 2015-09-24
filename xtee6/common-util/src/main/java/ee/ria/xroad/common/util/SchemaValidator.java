package ee.ria.xroad.common.util;

import java.net.URL;

import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import lombok.extern.slf4j.Slf4j;
import org.xml.sax.SAXException;

import ee.ria.xroad.common.CodedException;

/**
 * Base class for schema-based validators.
 */
@Slf4j
public class SchemaValidator {

    protected static Schema createSchema(String fileName) {
        SchemaFactory factory =
                SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        try {
            URL schemaLocation = ResourceUtils.getClasspathResource(fileName);
            return factory.newSchema(schemaLocation);
        } catch (SAXException e) {
            log.error("Creating schema from file '{}' failed",
                    fileName, e);
            throw new RuntimeException(
                    "Unable to create schema validator", e);
        }
    }

    protected static void validate(Schema schema, Source source,
            String errorCode) throws Exception {
        if (schema == null) {
            throw new IllegalStateException("Schema is not initialized");
        }

        try {
            Validator validator = schema.newValidator();
            validator.validate(source);
        } catch (SAXException e) {
            throw new CodedException(errorCode, e);
        }
    }

    @Override
    public String toString() {
        return super.toString();
    }
}
