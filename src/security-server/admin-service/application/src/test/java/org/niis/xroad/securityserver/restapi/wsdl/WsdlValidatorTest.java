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
package org.niis.xroad.securityserver.restapi.wsdl;

import ee.ria.xroad.common.util.process.ExternalProcessRunner;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.restapi.exceptions.DeviationCodes;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Test WSDLValidator
 * Tests external validator success and fail. This tests only one Spring component so instead of loading a whole
 * new Spring application context we are instantiating the tested component via it's constructor (WsdlValidator)
 */
public class WsdlValidatorTest {
    public static final String MOCK_VALIDATOR_WARNING = "WARNING: this can be ignored";
    public static final String MOCK_VALIDATOR = "src/test/resources/validator/mock-wsdlvalidator.sh";
    public static final String FOOBAR_VALIDATOR = "/bin/foobar-validator";

    private WsdlValidator wsdlValidator = new WsdlValidator(new ExternalProcessRunner());

    @Before
    public void setup() {
        ReflectionTestUtils.setField(wsdlValidator, "wsdlValidatorCommand", MOCK_VALIDATOR);
    }

    @Test
    public void validatorNotExecutable() throws Exception {
        ReflectionTestUtils.setField(wsdlValidator, "wsdlValidatorCommand", FOOBAR_VALIDATOR);
        try {
            wsdlValidator.executeValidator("src/test/resources/wsdl/error.wsdl");
            fail("should have thrown WsdlValidationException");
        } catch (WsdlValidator.WsdlValidatorNotExecutableException expected) {
        }
    }

    @Test
    public void shouldHandleWarnings() throws Exception {
        List<String> warnings = wsdlValidator.executeValidator("src/test/resources/wsdl/warning.wsdl");
        assertNotNull(warnings);
        assertEquals(1, warnings.size());
        assertEquals(Collections.singletonList(MOCK_VALIDATOR_WARNING), warnings);
    }

    @Test
    public void shouldFailValidation() throws Exception {
        try {
            wsdlValidator.executeValidator("src/test/resources/wsdl/error.wsdl");
            fail("should have thrown WsdlValidationException");
        } catch (WsdlValidator.WsdlValidationFailedException expected) {
            Assert.assertEquals(DeviationCodes.ERROR_INVALID_WSDL, expected.getErrorDeviation().getCode());
        }
    }

    @Test
    public void shouldPassValidation() throws Exception {
        List<String> warnings = wsdlValidator.executeValidator("src/test/resources/wsdl/testservice.wsdl");
        assertEquals(new ArrayList(), warnings);
    }
}
