/*
 * The MIT License
 *
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

package org.niis.xroad.proxy.core.util;

import ee.ria.xroad.common.identifier.XRoadId;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class IdentifierValidator {

    /**
     * Logs a warning if identifier contains invalid characters.
     */
    public static boolean checkIdentifier(final XRoadId id) {
        if (id != null) {
            if (!validateIdentifierField(id.getXRoadInstance())) {
                log.warn("Invalid character(s) in identifier {}", id);
                return false;
            }

            for (String f : id.getFieldsForStringFormat()) {
                if (f != null && !validateIdentifierField(f)) {
                    log.warn("Invalid character(s) in identifier {}", id);
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean validateIdentifierField(final CharSequence field) {
        for (int i = 0; i < field.length(); i++) {
            final char c = field.charAt(i);
            //ISO control char
            if (c <= '\u001f' || (c >= '\u007f' && c <= '\u009f')) {
                return false;
            }
            //Forbidden chars
            if (c == '%' || c == ':' || c == ';' || c == '/' || c == '\\' || c == '\u200b' || c == '\ufeff') {
                return false;
            }
            //"normalized path" check is redundant since path separators (/,\) are forbidden
        }
        return true;
    }

}
