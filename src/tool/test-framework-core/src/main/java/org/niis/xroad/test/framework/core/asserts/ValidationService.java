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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Service for validating assertions and attaching them to report.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ValidationService {

    private final AssertionsFormatter assertionsFormatter;
    private static final SpelExpressionParser PARSER = new SpelExpressionParser();
    private static final String EXPECTED_CONTEXT_VAR = "expected";

    /**
     * Validates multiple validations and attaches them to the report.
     *
     * @param validations to perform
     */
    public void validate(Collection<Validation> validations) {
        List<CompletedAssertion> completedAssertions = validations.stream()
                .map(this::doValidation)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        throwIfFailed(completedAssertions);
    }

    /**
     * Validates single validation and attaches it to the report
     *
     * @param validation to validate
     */
    public void validate(Validation validation) {
        List<CompletedAssertion> completedAssertions = doValidation(validation);
        throwIfFailed(completedAssertions);
    }

    private List<CompletedAssertion> doValidation(Validation validation) {
        EvaluationContext ctx = createSpElContext(validation.getContext());
        List<CompletedAssertion> completedAssertions = new ArrayList<>();

        boolean skip = false;
        for (Assertion assertion : validation.getAssertions()) {
            CompletedAssertion completedAssertion;
            if (!skip) {
                completedAssertion = doAssert(ctx, assertion, validation);
                skip = assertion.isSkipRestIfFailed() && completedAssertion.getStatus() == AssertionStatus.FAILED;
            } else {
                completedAssertion = new CompletedAssertion(assertion, validation.getBaseExpression(),
                        AssertionStatus.SKIPPED, "");
            }
            completedAssertions.add(completedAssertion);
        }
        assertionsFormatter.formatAndAttachToReport(validation, completedAssertions);
        return completedAssertions;
    }

    private EvaluationContext createSpElContext(Object rootObject) {
        StandardEvaluationContext ctx = new StandardEvaluationContext(rootObject);
        ctx.registerFunction("jsonPath", BeanUtils.resolveSignature("evaluate", JsonPathUtils.class));
        return ctx;
    }

    private CompletedAssertion doAssert(EvaluationContext ctx, Assertion assertion, Validation validation) {
        String baseExpression = validation.getBaseExpression();
        Object actualValue;
        try {
            actualValue = resolveActualValue(ctx, assertion, validation);
        } catch (Exception e) {
            log.trace("Failed to resolve assertion actual value from body {}", ctx.getRootObject().getValue(), e);
            return new CompletedAssertion(
                    assertion,
                    baseExpression,
                    AssertionStatus.FAILED,
                    "Assertion failed to parse/evaluate path: " + e.getMessage());
        }

        return switch (assertion.getOperation()) {
            case EQUALS -> assertEquals(assertion, baseExpression, actualValue);
            case NOT_EQUALS -> assertNotEquals(assertion, baseExpression, actualValue);
            case NULL -> assertNull(assertion, baseExpression, actualValue);
            case NOT_NULL -> assertNotNull(assertion, baseExpression, actualValue);
            case CONTAINS -> assertContains(assertion, baseExpression, actualValue);
            case LIST_CONTAINS -> assertListContains(assertion, baseExpression, actualValue);
            case LIST_CONTAINS_VALUE -> assertListOfExpectedValuesContainsActualValue(assertion, baseExpression, actualValue);
            case LIST_EXCLUDES -> assertListExcludes(assertion, baseExpression, actualValue);
            case LIST_EQUALS -> assertListContainsAll(assertion, baseExpression, actualValue);
            case EMPTY -> assertEmpty(assertion, baseExpression, actualValue);
            case EXPRESSION -> assertExpression(assertion, baseExpression, actualValue);
        };
    }

    private Object resolveActualValue(EvaluationContext ctx, Assertion assertion, Validation validation) {
        String expression = null;
        try {
            String baseExpression = validation.getBaseExpression();
            expression = getExpression(assertion, baseExpression);
            if (assertion.getExpectedValue() != null) {
                ctx.setVariable(EXPECTED_CONTEXT_VAR, assertion.getExpectedValue());
            }
            if (assertion.getContextValues() != null) {
                assertion.getContextValues().forEach(ctx::setVariable);
            }

            return assertion.getActualValue() != null ? assertion.getActualValue()
                    : PARSER.parseExpression(expression).getValue(ctx);
        } catch (Exception e) {
            log.debug("Assertion failed to parse/evaluate. Expression : {}", expression, e);
            throw e;
        }
    }

    private String getExpression(Assertion assertion, String baseExpression) {
        if (assertion.getExpressionType() == ExpressionType.JSON_PATH) {
            return String.format("#jsonPath(#root, '%s')", assertion.getExpression());
        }
        return assertion.getExpressionType() == ExpressionType.RELATIVE ? baseExpression + assertion.getExpression()
                : assertion.getExpression();
    }

    private CompletedAssertion assertEquals(Assertion assertion, String baseExpression, Object actualValue) {
        Object expectedValue = assertion.getExpectedValue();
        boolean equals = Objects.equals(expectedValue, actualValue);
        return new CompletedAssertion(
                assertion,
                baseExpression,
                equals ? AssertionStatus.OK : AssertionStatus.FAILED,
                actualValue);
    }

    private CompletedAssertion assertNotEquals(Assertion assertion, String baseExpression, Object actualValue) {
        Object expectedValue = assertion.getExpectedValue();
        boolean equals = Objects.equals(expectedValue, actualValue);
        return new CompletedAssertion(
                assertion,
                baseExpression,
                equals ? AssertionStatus.FAILED : AssertionStatus.OK,
                actualValue);
    }

    private CompletedAssertion assertNull(Assertion assertion, String baseExpression, Object actualValue) {
        boolean isNull = Objects.isNull(actualValue);
        return new CompletedAssertion(
                assertion,
                baseExpression,
                isNull ? AssertionStatus.OK : AssertionStatus.FAILED,
                isNull ? "NULL" : "NOT_NULL");
    }

    private CompletedAssertion assertNotNull(Assertion assertion, String baseExpression, Object actualValue) {
        boolean nonNull = Objects.nonNull(actualValue);
        return new CompletedAssertion(
                assertion,
                baseExpression,
                nonNull ? AssertionStatus.OK : AssertionStatus.FAILED,
                nonNull ? "NOT_NULL" : "NULL");
    }

    private CompletedAssertion assertContains(Assertion assertion, String baseExpression, Object actualValue) {
        if (!(actualValue instanceof String actualString)) {
            return new CompletedAssertion(
                    assertion,
                    baseExpression,
                    AssertionStatus.FAILED,
                    "Can not perform CONTAINS. Actual value not String!");
        }
        if (!(assertion.getExpectedValue() instanceof String expectedString)) {
            return new CompletedAssertion(
                    assertion,
                    baseExpression,
                    AssertionStatus.FAILED,
                    "Can not perform CONTAINS. Expected value not String!");
        }
        boolean contains = actualString.contains(expectedString);
        return new CompletedAssertion(
                assertion,
                baseExpression,
                contains ? AssertionStatus.OK : AssertionStatus.FAILED,
                actualString);
    }

    private CompletedAssertion assertListContains(Assertion assertion, String baseExpression, Object actualValue) {
        if (!(actualValue instanceof Collection<?> actualValues)) {
            return new CompletedAssertion(
                    assertion,
                    baseExpression,
                    AssertionStatus.FAILED,
                    "Can not perform LIST CONTAINS. Actual value not Collection!");
        }
        if (!(assertion.getExpectedValue() instanceof Collection<?> expectedValues)) {
            return new CompletedAssertion(
                    assertion,
                    baseExpression,
                    AssertionStatus.FAILED,
                    "Can not perform LIST CONTAINS. Expected value not Collection!");
        }

        for (Object expectedValue : expectedValues) {
            if (!actualValues.contains(expectedValue)) {
                return new CompletedAssertion(assertion, baseExpression, AssertionStatus.FAILED, actualValues);
            }
        }
        return new CompletedAssertion(assertion, baseExpression, AssertionStatus.OK, actualValues);
    }

    private CompletedAssertion assertListOfExpectedValuesContainsActualValue(Assertion assertion, String baseExpression,
                                                                             Object actualValue) {
        if (!(assertion.getExpectedValue() instanceof Collection<?> expectedValues)) {
            return new CompletedAssertion(
                    assertion,
                    baseExpression,
                    AssertionStatus.FAILED,
                    "Can not perform LIST CONTAINS ACTUAL VALUE. Expected value not Collection!");
        }
        if (!expectedValues.contains(actualValue)) {
            return new CompletedAssertion(assertion, baseExpression, AssertionStatus.FAILED, actualValue);
        } else {
            return new CompletedAssertion(assertion, baseExpression, AssertionStatus.OK, actualValue);
        }
    }

    private CompletedAssertion assertListExcludes(Assertion assertion, String baseExpression, Object actualValue) {
        if (!(actualValue instanceof List<?> actualValues)) {
            return new CompletedAssertion(
                    assertion,
                    baseExpression,
                    AssertionStatus.FAILED,
                    "Can not perform LIST CONTAINS. Actual value not List!");
        }
        if (!(assertion.getExpectedValue() instanceof List<?> nonExpectedValues)) {
            return new CompletedAssertion(
                    assertion,
                    baseExpression,
                    AssertionStatus.FAILED,
                    "Can not perform LIST CONTAINS. Expected value not List!");
        }

        boolean excludes = true;
        for (Object nonExpectedValue : nonExpectedValues) {
            if (actualValues.contains(nonExpectedValue)) {
                excludes = false;
                break;
            }
        }
        return new CompletedAssertion(
                assertion,
                baseExpression,
                excludes ? AssertionStatus.OK : AssertionStatus.FAILED,
                actualValues);
    }

    private CompletedAssertion assertListContainsAll(Assertion assertion, String baseExpression, Object actualValue) {
        if (!(actualValue instanceof Collection<?> actualValues)) {
            return new CompletedAssertion(
                    assertion,
                    baseExpression,
                    AssertionStatus.FAILED,
                    "Can not perform LIST CONTAINS. Actual value not Collection!");
        }
        if (!(assertion.getExpectedValue() instanceof Collection<?> expectedValues)) {
            return new CompletedAssertion(
                    assertion,
                    baseExpression,
                    AssertionStatus.FAILED,
                    "Can not perform LIST CONTAINS. Expected value not Collection!");
        }

        if (actualValues.containsAll(expectedValues) && expectedValues.containsAll(actualValues)) {
            return new CompletedAssertion(
                    assertion,
                    baseExpression,
                    AssertionStatus.OK,
                    actualValues);
        } else {
            return new CompletedAssertion(
                    assertion,
                    baseExpression,
                    AssertionStatus.FAILED,
                    actualValues);
        }
    }

    private CompletedAssertion assertEmpty(Assertion assertion, String baseExpression, Object actualValue) {
        if (actualValue instanceof Collection<?> collection) {
            boolean isEmpty = collection.isEmpty();
            return new CompletedAssertion(
                    assertion,
                    baseExpression,
                    isEmpty ? AssertionStatus.OK : AssertionStatus.FAILED,
                    isEmpty ? "EMPTY" : "NOT_EMPTY");
        } else if (actualValue != null && actualValue.getClass().isArray()) {
            boolean isEmpty = java.lang.reflect.Array.getLength(actualValue) == 0;
            return new CompletedAssertion(
                    assertion,
                    baseExpression,
                    isEmpty ? AssertionStatus.OK : AssertionStatus.FAILED,
                    isEmpty ? "EMPTY" : "NOT_EMPTY");
        } else {
            throw new AssertionError("Object is not a list and cannot be compared");
        }
    }

    private CompletedAssertion assertExpression(Assertion assertion, String baseExpression, Object actualValue) {
        if (!(actualValue instanceof Boolean booleanValue)) {
            return new CompletedAssertion(
                    assertion,
                    baseExpression,
                    AssertionStatus.FAILED,
                    "Expression operation returned NON boolean value");
        }
        return new CompletedAssertion(
                assertion,
                baseExpression,
                booleanValue ? AssertionStatus.OK : AssertionStatus.FAILED,
                actualValue);
    }

    private void throwIfFailed(List<CompletedAssertion> completedAssertions) {
        List<CompletedAssertion> failedAssertions = completedAssertions.stream()
                .filter(assertion -> assertion.getStatus() == AssertionStatus.FAILED)
                .collect(Collectors.toList());

        if (!failedAssertions.isEmpty()) {
            String message = assertionsFormatter.formatIntoErrorMessage(failedAssertions);
            failedAssertions.forEach(assertion -> log.info("Failed assertion: {}", assertion));
            throw new AssertionError("Assertions failed! Find more details in the attachment.\n" + message);
        }
    }
}
