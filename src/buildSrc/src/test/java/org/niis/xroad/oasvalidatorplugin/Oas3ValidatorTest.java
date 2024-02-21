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
package org.niis.xroad.oasvalidatorplugin;

import org.junit.jupiter.api.Test;
import org.openapi4j.core.exception.ResolutionException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class Oas3ValidatorTest {

    @Test
    void validateApiSpecNotFound() {
        try {
            Oas3Validator.validate("src/test/resources/not-found.yaml");
            fail("Should throw exception");
        } catch (ResolutionException e) {
            //success
        }
    }

    @Test
    void validateApiSpecSuccess() throws Exception {
        ApiValidationResults results = Oas3Validator.validate("src/test/resources/petstore-validation-success.yaml");
        assertTrue(results.getSpecificationValidationResult().isSuccess());
        assertTrue(results.getStyleValidationResult().isSuccess());
    }

    @Test
    void validateApiSpecFail() throws Exception {
        ApiValidationResults results = Oas3Validator.validate("src/test/resources/petstore-validation-fail.yaml");
        assertFalse(results.getSpecificationValidationResult().isSuccess());
        assertTrue(results.getStyleValidationResult().isSuccess());
    }

    @Test
    void validateApiSpecStyleFail() throws Exception {
        ApiValidationResults results = Oas3Validator.validate("src/test/resources/petstore-validation-style-fail.yaml");
        assertTrue(results.getSpecificationValidationResult().isSuccess());
        assertFalse(results.getStyleValidationResult().isSuccess());
    }
}
