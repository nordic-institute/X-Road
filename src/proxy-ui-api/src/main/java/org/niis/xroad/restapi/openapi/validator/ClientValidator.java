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
package org.niis.xroad.restapi.openapi.validator;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.niis.xroad.restapi.openapi.model.Client;
import org.niis.xroad.restapi.openapi.validator.EncodedIdentifierValidator.ValidationError;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Slf4j
public class ClientValidator implements Validator {
    @Override
    public boolean supports(Class<?> clazz) {
        return Client.class.equals(clazz);
    }

    private String getErrorCode(ValidationError validationError) {
        // TO DO: better names and add error messages also
        return validationError.name().toLowerCase();
    }

    @Override
    public void validate(Object target, Errors errors) {
        Client client = (Client) target;
        String memberCode = client.getMemberCode();
        EncodedIdentifierValidator validator = new EncodedIdentifierValidator();
        validator.getValidationErrors(memberCode).forEach(error ->
                errors.rejectValue("memberCode", getErrorCode(error), null, getErrorCode(error)));

        String subsystemCode = client.getSubsystemCode();
        if (!StringUtils.isBlank(subsystemCode)) {
            validator.getValidationErrors(subsystemCode).forEach(error ->
                    errors.rejectValue("subsystemCode", getErrorCode(error), null, getErrorCode(error)));
        }
    }
}
