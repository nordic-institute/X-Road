/**
 * The MIT License
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
package org.niis.xroad.restapi.wsdl;

import org.junit.Test;
import org.niis.xroad.restapi.exceptions.WsdlValidationException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Test WSDLValidator
 * Tests external validator success and fail
 */
public class WsdlValidatorTest {

    public static final String MOCK_VALIDATOR_ERROR = "ERROR: this is not fine";
    public static final String MOCK_VALIDATOR_WARNING = "WARNING: this can be ignored";

    @Test
    public void validatorNotExecutable() {
        WsdlValidator wsdlValidator = new WsdlValidator();
        wsdlValidator.setWsdlValidatorCommand("/bin/foobar-validator");
        try {
            wsdlValidator.executeValidator("src/test/resources/error.wsdl");
            fail("should have thrown WsdlValidationException");
        } catch (WsdlValidationException expected) {
            assertEquals(WsdlValidator.WSDL_VALIDATOR_NOT_EXECUTABLE, expected.getError().getCode());
            assertNull(expected.getError().getMetadata());
            assertNull(expected.getWarnings());
        }
    }

    @Test
    public void shouldHandleWarnings() {
        WsdlValidator wsdlValidator = new WsdlValidator();
        wsdlValidator.setWsdlValidatorCommand("src/test/resources/validator/mock-wsdlvalidator.sh");
        List<String> warnings = wsdlValidator.executeValidator("src/test/resources/warning.wsdl");
        assertNotNull(warnings);
        assertEquals(1, warnings.size());
        assertEquals(Collections.singletonList(MOCK_VALIDATOR_WARNING), warnings);
    }

    @Test
    public void shouldFailValidation() {
        WsdlValidator wsdlValidator = new WsdlValidator();
        wsdlValidator.setWsdlValidatorCommand("src/test/resources/validator/mock-wsdlvalidator.sh");
        try {
            wsdlValidator.executeValidator("src/test/resources/error.wsdl");
            fail("should have thrown WsdlValidationException");
        } catch (WsdlValidationException expected) {
            assertEquals(WsdlValidator.WSDL_VALIDATION_FAILED, expected.getError().getCode());
            assertNotNull(expected.getError().getMetadata());
            assertEquals(Collections.singletonList(MOCK_VALIDATOR_ERROR), expected.getError().getMetadata());
        }
    }


    @Test
    public void shouldPassValidation() {
        WsdlValidator wsdlValidator = new WsdlValidator();
        wsdlValidator.setWsdlValidatorCommand("src/test/resources/validator/mock-wsdlvalidator.sh");
        List<String> warnings = wsdlValidator.executeValidator("src/test/resources/testservice.wsdl");
        assertEquals(new ArrayList(), warnings);
    }
}
