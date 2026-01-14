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

package org.niis.xroad.test.framework.core.asserts;

/**
 * Helper class to create most common assertions.
 */
public final class Assertions {

    private Assertions() {
        // Utility class
    }

    /**
     * Creates assertion to check if field is not null.
     */
    public static Assertion notNullAssertion(String expression) {
        return Assertion.builder()
                .message("Field is not null")
                .expression(expression)
                .operation(AssertionOperation.NOT_NULL)
                .build();
    }

    /**
     * Creates equals assertion.
     */
    public static Assertion equalsAssertion(Object expected, String actualValuePath, String message) {
        return Assertion.builder()
                .message(message)
                .expression(actualValuePath)
                .expectedValue(expected)
                .build();
    }

    /**
     * Creates equals assertion.
     */
    public static Assertion equalsAssertion(Object expected, String actualValuePath) {
        return Assertion.builder()
                .message("Assert equals")
                .expression(actualValuePath)
                .expectedValue(expected)
                .build();
    }

    public static Assertion notEqualsAssertion(Object expected, String actualValuePath) {
        return Assertion.builder()
                .operation(AssertionOperation.NOT_EQUALS)
                .message("Assert not equals")
                .expression(actualValuePath)
                .expectedValue(expected)
                .build();
    }
}
