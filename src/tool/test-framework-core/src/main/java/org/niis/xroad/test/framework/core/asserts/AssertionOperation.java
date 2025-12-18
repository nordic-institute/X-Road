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
 * Defines available assertion operations
 */
public enum AssertionOperation {
    /**
     * Operation where value returned by applying expression is checked to be equal to the expectedValue
     * expectedValue.equals(actualValue)
     */
    EQUALS,

    /**
     * Operation where value returned by applying expression is checked to be not equal to the expectedValue
     * !expectedValue.equals(actualValue)
     */
    NOT_EQUALS,

    /**
     * Operation where value returned by applying expression is expected to be null
     * actualValue == null
     */
    NULL,

    /**
     * Operation where value returned by applying expression is expected to be not null
     * actualValue != null
     */
    NOT_NULL,

    /**
     * Operation where value returned by applying expression is expected to be a String and to be contained in the expected value.
     * expectedValue.contains(actualValue) == true
     */
    CONTAINS,

    /**
     * Operation where value returned by applying expression is expected to be a List and to be contained in the expected value.
     * expectedValue.contains(actualValue) == true
     */
    LIST_CONTAINS,

    /**
     * Operation where value returned by applying expression is expected to be an object and to be contained in expected value which
     * is expected to be a collection.
     * expectedValues.contains(actualValue) == true
     */
    LIST_CONTAINS_VALUE,

    /**
     * Operation where value returned by applying expression is expected to be a List and to be contained in the expected value.
     * expectedValue.contains(actualValue) == false
     */
    LIST_EXCLUDES,

    /**
     * Operation where value returned by applying expression is expected to be a List and to be contained in the expected value.
     * expectedValue.containsAll(actualValue) && actualValue.containsAll(expectedValue) == true
     */
    LIST_EQUALS,

    /**
     * Operation where value returned by applying expression is expected to be a List which should be empty.
     * actualValue.isEmpty() == true
     */
    EMPTY,

    /**
     * Operation where assertion is performed by evaluating the expression.
     * In such case expression has to return a boolean
     */
    EXPRESSION
}
