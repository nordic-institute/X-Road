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
package org.niis.xroad.restapi.util;

import org.niis.xroad.restapi.exceptions.NotFoundException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;

/**
 * Format utils
 */
public final class FormatUtils {
    private FormatUtils() {
        // noop
    }

    /**
     * Converts Date to OffsetDateTime with ZoneOffset.UTC
     * @param date
     * @return OffsetDateTime with offset ZoneOffset.UTC
     * @see ZoneOffset#UTC
     */
    public static OffsetDateTime fromDateToOffsetDateTime(Date date) {
        return date.toInstant().atOffset(ZoneOffset.UTC);
    }

    /**
     * in case of NumberFormatException we throw NotFoundException. Client should not
     * know about id parameter details, such as "it should be numeric" -
     * the resource with given id just cant be found, and that's all there is to it
     * @param id as String
     * @return id as Long
     */
    public static Long parseLongIdOrThrowNotFound(String id) throws NotFoundException {
        Long groupId = null;
        try {
            groupId = Long.valueOf(id);
        } catch (NumberFormatException nfe) {
            throw new NotFoundException(nfe);
        }
        return groupId;
    }
}
