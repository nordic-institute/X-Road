/**
 * The MIT License
 * <p>
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.cs.admin.jpa.repository.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

/**
 * Utility for working with CriteriaBuilder
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CriteriaBuilderUtil {

    public static final char LIKE_EXPRESSION_ESCAPE_CHAR = '\\';
    public static final String LIKE_EXPRESSION_ESCAPE_STRING = String.valueOf(LIKE_EXPRESSION_ESCAPE_CHAR);

    /**
     * Create a case-insensite LIKE expression Predicate. Also escape special characters \, % and _
     */
    public static Predicate caseInsensitiveLike(Root root, CriteriaBuilder builder, String s, Expression expression) {
        return builder.like(
                builder.lower(expression),
                builder.lower(builder.literal("%" + escapeSpecialChars(s) + "%")),
                LIKE_EXPRESSION_ESCAPE_CHAR
        );
    }

    private static String escapeSpecialChars(String s) {
        return s.replace(LIKE_EXPRESSION_ESCAPE_STRING, LIKE_EXPRESSION_ESCAPE_STRING + LIKE_EXPRESSION_ESCAPE_STRING)
                .replace("%", LIKE_EXPRESSION_ESCAPE_STRING + "%")
                .replace("_", LIKE_EXPRESSION_ESCAPE_STRING + "_");
    }


}
