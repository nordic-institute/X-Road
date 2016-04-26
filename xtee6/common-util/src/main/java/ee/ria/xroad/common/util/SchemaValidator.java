/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
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
