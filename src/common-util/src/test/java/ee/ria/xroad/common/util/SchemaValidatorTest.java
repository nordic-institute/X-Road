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

import ee.ria.xroad.common.ErrorCodes;

import lombok.SneakyThrows;
import org.junit.Rule;
import org.junit.Test;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import java.net.URL;
import java.nio.file.Paths;

import static org.junit.Assert.fail;

/**
 * Tests to verify schema validator behavior.
 */
public class SchemaValidatorTest {

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    /**
     * Tests Ok XML validation.
     *
     * @throws Exception in case error occurs.
     */
    @Test
    public void testOkValidation() throws Exception {
        StreamSource source = new StreamSource(ResourceUtils.getClasspathResourceStream("ocsp-fetchinterval-part.xml"));
        TestValidator.validate(source);
    }

    /**
     * Test to ensure that SchemaValidator catches XXE.
     *
     * @throws Exception in case of XXE found while parsing the test XML
     */
    @Test
    public void testXxeFailsValidation() throws Exception {
        thrown.expectError(ErrorCodes.X_MALFORMED_OPTIONAL_PARTS_CONF);

        StreamSource source = new StreamSource(
                ResourceUtils.getClasspathResourceStream("ocsp-fetchinterval-part-with-xxe.xml"));

        TestValidator.validate(source);

        fail("Should fail to parse XML containing XXE. But it passed validation.");
    }

    private static class TestValidator extends SchemaValidator {
        private static Schema schema;

        static {
            schema = createSchema("../common-verifier/src/main/resources/ocsp-fetchinterval-conf.xsd");
        }

        @SneakyThrows
        protected static Schema createSchema(String fileName) {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

            try {
                URL schemaLocation = Paths.get(fileName).toUri().toURL();

                return factory.newSchema(schemaLocation);
            } catch (SAXException e) {
                throw new RuntimeException("Unable to create schema validator", e);
            }
        }

        static void validate(Source source) throws Exception {
            validate(schema, source, ErrorCodes.X_MALFORMED_OPTIONAL_PARTS_CONF);
        }
    }
}


