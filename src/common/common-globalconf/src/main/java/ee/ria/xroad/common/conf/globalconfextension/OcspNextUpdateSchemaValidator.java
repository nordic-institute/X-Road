/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package ee.ria.xroad.common.conf.globalconfextension;

import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.common.util.SchemaValidator;

import org.apache.commons.io.FileUtils;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;

import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

/**
 * Validator for Ocsp next update
 */
public class OcspNextUpdateSchemaValidator extends SchemaValidator {

    private static Schema schema;

    static {
        schema = createSchema("ocsp-nextupdate-conf.xsd");
    }

    /**
     * Program entry point
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Please supply one argument, file name of the validated ocsp next update parameters xml.");
        }
        new OcspNextUpdateSchemaValidator().validateFile(args[0]);
    }

    /**
     *  Validates the given XML file
     */
    public void validateFile(String fileName) throws Exception {
        String xml = FileUtils.readFileToString(new File(fileName),
                StandardCharsets.UTF_8.toString());
        validate(xml);
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
        validate(schema, source, ErrorCodes.X_MALFORMED_OPTIONAL_PARTS_CONF);
    }
}
