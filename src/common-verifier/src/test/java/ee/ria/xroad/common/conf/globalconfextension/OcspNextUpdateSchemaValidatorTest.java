/**
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

import ee.ria.xroad.common.util.ResourceUtils;

import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.File;
import java.net.URL;

/**
 * Tests for {@link OcspNextUpdateSchemaValidator}
 */
public class OcspNextUpdateSchemaValidatorTest {
    @Rule
    public final ExpectedException exception = ExpectedException.none();

    private static final String VALID_FILE = "valid-nextupdate-params.xml";
    private static final String EMPTY_FILE = "empty-nextupdate-params.xml";
    private static final String INVALID_FILE = "invalid-nextupdate-params.xml";

    private String getClasspathFilename(String fileName) {
        URL schemaLocation = ResourceUtils.class.getClassLoader().getResource(fileName);
        File f = FileUtils.toFile(schemaLocation);
        return f.getPath();
    }

    @Test
    public void testValidConfiguration() throws Exception {
        OcspNextUpdateSchemaValidator validator = new OcspNextUpdateSchemaValidator();
        validator.validateFile(getClasspathFilename(VALID_FILE));
    }

    @Test
    public void testEmptyConfigurationIsInvalid() throws Exception {
        OcspNextUpdateSchemaValidator validator = new OcspNextUpdateSchemaValidator();
        exception.expect(Exception.class);
        validator.validateFile(getClasspathFilename(EMPTY_FILE));
    }

    @Test
    public void testInvalidConfiguration() throws Exception {
        OcspNextUpdateSchemaValidator validator = new OcspNextUpdateSchemaValidator();
        exception.expect(Exception.class);
        validator.validateFile(getClasspathFilename(INVALID_FILE));
    }
}
