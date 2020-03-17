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

import java.util.EnumSet;

/**
 * Validates strings to detect if they can be used to build encoded
 * ids such as FI:GOV:M1:SUBSYSTEM_1.
 * One part of that id is subsystem code "SUBSYSTEM_1" and in order
 * to be usable in resource paths in our API, these kinds of character
 * sequences are forbidden:
 *
 * - non-normalized paths: character sequences creating a path traversal sequence like "./", "/../" or "/."
 * - characters that are not printable ASCII characters
 * - colons (":"), since we use that as encoded ID identifier separator (e.g. "FI:GOV:123:SUBSYSTEM1")
 * - semicolons (";")
 * - slash (even URL encoded) ("/")
 * - backslash("\")
 * - percent (even URL encoded) ("%")
 *
 * TO DO:
 * - wont check for non-printable ASCII chars, since we are validating
 * decoded chars, and it must be possible to use also other than printable
 * ascii characters. REST API urls must only use printable ASCII chars, but
 * they can use url encoding to encode the non-printable chars (unless those
 * are forbidden, like e.g. encoded slash is)
 *
 */
public class EncodedIdentifierValidator {

    private static final String FORBIDDEN_COLON = ":";

    public EnumSet<ValidationError> getValidationErrors(String s) {
        EnumSet<ValidationError> errors = EnumSet.noneOf(ValidationError.class);
        if (s.contains(FORBIDDEN_COLON)) {
            errors.add(ValidationError.COLON);
        }
        if (SpringFirewallLogic.containsBackslash(s)) {
            errors.add(ValidationError.BACKSLASH);
        }
        if (SpringFirewallLogic.containsForwardslash(s)) {
            errors.add(ValidationError.FORWARDSLASH);
        }
        if (SpringFirewallLogic.containsPercent(s)) {
            errors.add(ValidationError.PERCENT);
        }
        if (SpringFirewallLogic.containsSemicolon(s)) {
            errors.add(ValidationError.SEMICOLON);
        }
        if (!SpringFirewallLogic.isNormalized(s)) {
            errors.add(ValidationError.NON_NORMALIZED_PATH);
        }
        return errors;
    }

    public enum ValidationError {
        NON_NORMALIZED_PATH,
        COLON,
        SEMICOLON,
        FORWARDSLASH,
        BACKSLASH,
        PERCENT,
//        NOT_PRINTABLE_ASCII,
    }
}
