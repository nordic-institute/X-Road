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
package ee.ria.xroad.common.validation;

import java.util.EnumSet;

/**
 * Validates strings to detect if they can be used to build encoded
 * ids such as FI:GOV:M1:SUBSYSTEM_1.
 * One part of that id is subsystem code "SUBSYSTEM_1" and in order
 * to be usable in resource paths in our API, these kinds of character
 * sequences are forbidden:
 * <p>
 * - non-normalized paths: character sequences creating a path traversal sequence like "./", "/../" or "/."
 * - colons (":"), since we use that as encoded ID identifier separator (e.g. "FI:GOV:123:SUBSYSTEM1")
 * - semicolons (";")
 * - slash ("/")
 * - backslash ("\")
 * - percent ("%")
 * <p>
 * Spring Firewall checks for url-encoded characters - such as semicolon "%3B" - but this validator does
 * not. This validator is intended for use in controllers, for validating JSON request body, where
 * properties are not url-encoded.
 *
 */
class LegacyEncodedIdentifierValidator implements IdentifierValidator {

    EnumSet<ValidationError> getValidationErrors(String s) {
        EnumSet<ValidationError> errors = EnumSet.noneOf(ValidationError.class);
        if (s == null) {
            return errors;
        }
        if (SpringFirewallValidationRules.containsColon(s)) {
            errors.add(ValidationError.COLON);
        }
        if (SpringFirewallValidationRules.containsBackslash(s)) {
            errors.add(ValidationError.BACKSLASH);
        }
        if (SpringFirewallValidationRules.containsForwardslash(s)) {
            errors.add(ValidationError.FORWARDSLASH);
        }
        if (SpringFirewallValidationRules.containsPercent(s)) {
            errors.add(ValidationError.PERCENT);
        }
        if (SpringFirewallValidationRules.containsSemicolon(s)) {
            errors.add(ValidationError.SEMICOLON);
        }
        if (StringValidationUtils.containsControlChars(s)) {
            errors.add(ValidationError.CONTROL_CHAR);
        }
        return errors;
    }

    @Override
    public boolean isValid(String s) {
        return getValidationErrors(s).isEmpty();
    }

    enum ValidationError {
        COLON,
        SEMICOLON,
        FORWARDSLASH,
        BACKSLASH,
        PERCENT,
        CONTROL_CHAR
    }
}
