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
package org.niis.xroad.cs.test.glue;

import com.nortal.test.asserts.Assertion;
import com.nortal.test.asserts.AssertionOperation;
import com.nortal.test.asserts.JsonPathAssertions;
import com.nortal.test.asserts.ValidationHelper;
import com.nortal.test.asserts.ValidationService;
import com.nortal.test.core.services.CucumberScenarioProvider;
import com.nortal.test.core.services.ScenarioContext;
import feign.FeignException;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.cs.test.container.service.MockServerService;
import org.niis.xroad.cs.test.container.service.TestDatabaseService;
import org.opentest4j.AssertionFailedError;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

import static com.nortal.test.asserts.Assertions.equalsAssertion;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;

/**
 * Base class for all step definitions. Provides convenience methods and most commonly used beans.
 */
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public abstract class BaseStepDefs {
    @Autowired
    private ScenarioContext scenarioContext;
    @Autowired
    protected CucumberScenarioProvider cucumberScenarioProvider;
    @Autowired
    protected ValidationService validationService;
    @Autowired
    protected MockServerService mockServerService;
    @Autowired
    protected TestDatabaseService testDatabaseService;

    protected Assertion equalsStatusCodeAssertion(HttpStatus expected) {
        return new Assertion.Builder()
                .message("Verify status code")
                .expression("statusCode")
                .expectedValue(expected)
                .build();
    }

    protected Assertion collectionEqualsAssertion(Collection<?> expected, String expression) {
        return new Assertion.Builder()
                .message("Verify collection equals")
                .operation(AssertionOperation.LIST_EQUALS)
                .expression(expression)
                .expectedValue(expected)
                .build();
    }

    protected Assertion isTrue(String expression) {
        return equalsAssertion(true, expression, "Expression should evaluate to True");
    }

    protected Assertion isFalse(String expression) {
        return equalsAssertion(false, expression, "Expression should evaluate to False");
    }

    protected Assertion isNull(String expression) {
        return new Assertion.Builder()
                .message("Assert field is null")
                .expression(expression)
                .operation(AssertionOperation.NULL)
                .build();
    }

    protected Assertion equalsStatusCodeAssertion(int actualValue, HttpStatus expectedValue) {
        return new Assertion.Builder()
                .message("Verify status code")
                .expression("=")
                .actualValue(actualValue)
                .expectedValue(expectedValue.value())
                .build();
    }

    protected ValidationHelper validate(Object context) {
        return new ValidationHelper(validationService, context, "Validate response");
    }

    protected void validateErrorResponse(int expectedStatus, String expectedErrorCode, FeignException feignException) {
        validate(feignException.contentUTF8())
                .assertion(new Assertion.Builder()
                        .message("Verify status code")
                        .expression("=")
                        .actualValue(feignException.status())
                        .expectedValue(expectedStatus)
                        .build())
                .assertion(JsonPathAssertions.equalsAssertion(expectedErrorCode, "$.error.code"))
                .execute();
    }

    /**
     * Put a value in scenario context. Value can be accessed through getStepData.
     *
     * @param key   value key. Non-null.
     * @param value value
     */
    protected void putStepData(StepDataKey key, Object value) {
        scenarioContext.putStepData(key.name(), value);
    }

    /**
     * Get value from scenario context.
     *
     * @param key value key
     * @return value from the context
     */
    protected <T> Optional<T> getStepData(StepDataKey key) {
        return Optional.ofNullable(scenarioContext.getStepData(key.name()));
    }

    /**
     * Get value from scenario context.
     *
     * @param key value key
     * @return value from the context
     * @throws AssertionFailedError thrown if value is missing
     */
    protected <T> T getRequiredStepData(StepDataKey key) throws AssertionFailedError {
        return scenarioContext.getRequiredStepData(key.name());
    }

    /**
     * Generate random member id.
     *
     * @param parts - number of id parts.
     * @return generated id
     */
    @SuppressWarnings("checkstyle:MagicNumber")
    protected String randomMemberId(int parts) {
        String[] idParts = new String[parts];
        Arrays.setAll(idParts, index -> randomAlphabetic(3));
        return StringUtils.join(idParts, ":");
    }

    /**
     * Safely parse string to integer. Null-safe
     *
     * @param value value to convert
     * @return integer or null
     */
    protected Integer safeToInt(String value) {
        if (value == null) {
            return null;
        }
        return Integer.valueOf(value);
    }

    /**
     * An enumerated key for data transfer between steps.
     */
    public enum StepDataKey {
        RESPONSE,
        RESPONSE_BODY,
        ERROR_RESPONSE_BODY,
        RESPONSE_STATUS,
        CERTIFICATION_SERVICE_ID,
        OCSP_RESPONDER_ID,
        NEW_OCSP_RESPONDER_URL,
        TOKEN_TYPE,
        RESULT_LIST,
    }
}
