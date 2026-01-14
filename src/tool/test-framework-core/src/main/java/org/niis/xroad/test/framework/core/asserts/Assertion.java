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

import lombok.Getter;

import java.util.Map;

/**
 * This class represents an assertion that will be carried out.
 */
@Getter
public class Assertion {

    private final String message;
    private final String expression;
    private final ExpressionType expressionType;
    private final AssertionOperation operation;
    private final Object expectedValue;
    private final Map<String, Object> contextValues;
    private final boolean skipRestIfFailed;
    private final Object actualValue;

    public Assertion(String message, String expression, ExpressionType expressionType, AssertionOperation operation,
            Object expectedValue, Map<String, Object> contextValues, boolean skipRestIfFailed, Object actualValue) {
        this.message = message;
        this.expression = expression != null ? expression : "";
        this.expressionType = expressionType != null ? expressionType : ExpressionType.RELATIVE;
        this.operation = operation != null ? operation : AssertionOperation.EQUALS;
        this.expectedValue = expectedValue;
        this.contextValues = contextValues;
        this.skipRestIfFailed = skipRestIfFailed;
        this.actualValue = actualValue;
    }

    private Assertion(Builder builder) {
        this.message = builder.message != null ? builder.message : "";
        if (builder.expression == null) {
            throw new NullPointerException();
        }
        this.expression = builder.expression;
        this.expressionType = builder.expressionType != null ? builder.expressionType : ExpressionType.RELATIVE;
        this.operation = builder.operation != null ? builder.operation : AssertionOperation.EQUALS;
        this.expectedValue = builder.expectedValue;
        this.contextValues = builder.contextValues;
        this.skipRestIfFailed = builder.skipRestIfFailed != null ? builder.skipRestIfFailed : false;
        this.actualValue = builder.actualValue;
    }

    public static class Builder {
        private String message;
        private String expression;
        private ExpressionType expressionType;
        private AssertionOperation operation;
        private Object expectedValue;
        private Map<String, Object> contextValues;
        private Boolean skipRestIfFailed;
        private Object actualValue;

        public Builder() {
        }

        private Builder(Assertion assertion) {
            this.message = assertion.message;
            this.expression = assertion.expression;
            this.expressionType = assertion.expressionType;
            this.operation = assertion.operation;
            this.expectedValue = assertion.expectedValue;
            this.contextValues = assertion.contextValues;
            this.skipRestIfFailed = assertion.skipRestIfFailed;
            this.actualValue = assertion.actualValue;
        }

        /**
         * Assertion messages describing what the assertion does.
         * e.g. Verify ratePlanSoc or Verify correct line count
         */
        public Builder message(String value) {
            this.message = value;
            return this;
        }

        /**
         * SpEL expression that retrieves actual value from the context.
         * Expectation here is that once the expression is parsed and getValue is
         * executed on the context whatever actualValue is returned will
         * pass the selected operation against the expectedValue.
         * <p>
         * Some default variables can be used in the expression:
         * #root to reference the root context object
         * #expected to reference the expectedValue
         */
        public Builder expression(String value) {
            this.expression = value;
            return this;
        }

        public Builder expressionType(ExpressionType value) {
            this.expressionType = value;
            return this;
        }

        /**
         * Operation that will be executed using expected and actual values as operands
         */
        public Builder operation(AssertionOperation value) {
            this.operation = value;
            return this;
        }

        /**
         * Expected value.
         * Depending on the selected operation may be optional (e.g. for NOT_NULL)
         */
        public Builder expectedValue(Object value) {
            this.expectedValue = value;
            return this;
        }

        /**
         * Context values.
         * Designed to be used in tandem with EXPRESSION
         */
        public Builder contextValues(Map<String, Object> value) {
            this.contextValues = value;
            return this;
        }

        /**
         * Some assertions can act as gatekeepers, meaning that if they fail, there is
         * little meaning in running the rest of them.
         * If TRUE will skip the rest of assertions.
         * FALSE by default
         */
        public Builder skipRestIfFailed(boolean value) {
            this.skipRestIfFailed = value;
            return this;
        }

        /**
         * Some assertions are just simple assertions where you would want to compare
         * two numbers and check if they match
         * and for them to show up in the report. For this actual value can be set.
         */
        public Builder actualValue(Object value) {
            this.actualValue = value;
            return this;
        }

        public Assertion build() {
            return new Assertion(this);
        }

        public static Builder fromAssertion(Assertion assertion) {
            return new Builder(assertion);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
