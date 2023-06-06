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
package org.niis.xroad.ss.test.glue;

import com.nortal.test.asserts.Assertion;
import com.nortal.test.asserts.JsonPathAssertions;
import io.cucumber.java.ParameterType;
import io.cucumber.java.en.Step;
import org.niis.xroad.ss.test.utils.ScenarioValueEvaluator;

import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.niis.xroad.ss.test.glue.BaseStepDefs.StepDataKey.RESULT_LIST;
import static org.niis.xroad.ss.test.utils.AssertionUtils.isTheListSorted;

public class CommonStepDefs extends BaseStepDefs {

    /**
     * Token types which are mapped to liquibase 'apikey-testdata' changeset.
     */
    public enum TokenType {
        SYSTEM_ADMINISTRATOR("d56e1ca7-4134-4ed4-8030-5f330bdb602a"),
        REGISTRATION_OFFICER("4a5842e5-4ede-49f1-ab32-1b6be33d81c3"),
        SECURITY_OFFICER("3964334d-1f65-4629-a4a4-73c62ade0c9c"),
        MANAGEMENT_SERVICE("de628164-9485-409c-b654-7dda28bb3872");

        private final String token;

        public String getHeaderToken() {
            return String.format("X-ROAD-APIKEY TOKEN=%s", this.token);
        }

        TokenType(String token) {
            this.token = token;
        }

        public static TokenType fromString(String value) {
            return valueOf(value.trim().toUpperCase());
        }
    }

    @Step("Authentication header is set to {tokenType}")
    public void authenticationHeaderIsSet(TokenType type) {
        putStepData(StepDataKey.TOKEN_TYPE, type);
    }

    @ParameterType("SYSTEM_ADMINISTRATOR|REGISTRATION_OFFICER|SECURITY_OFFICER|MANAGEMENT_SERVICE")
    public TokenType tokenType(String name) {
        return TokenType.fromString(name);
    }

    @Step("Response is of status code {int}")
    public void systemStatusIsValidated(int statusCode) {
        int responseCode = getRequiredStepData(StepDataKey.RESPONSE_STATUS);

        validate(responseCode)
                .assertion(new Assertion.Builder()
                        .message("Verify status code")
                        .expression("=")
                        .actualValue(responseCode)
                        .expectedValue(statusCode)
                        .build())
                .execute();
    }

    @Step("Response is of status code {int} and error code {string}")
    public void systemStatusIsValidated(int statusCode, String errorCode) {
        int responseCode = getRequiredStepData(StepDataKey.RESPONSE_STATUS);
        String errorResponse = getRequiredStepData(StepDataKey.ERROR_RESPONSE_BODY);

        var validation = validate(errorResponse)
                .assertion(new Assertion.Builder()
                        .message("Verify status code")
                        .expression("=")
                        .actualValue(responseCode)
                        .expectedValue(statusCode)
                        .build());

        if (!ScenarioValueEvaluator.isValidationIgnored(errorCode)) {
            validation.assertion(JsonPathAssertions.equalsAssertion(errorCode, "$.error.code"));
        }
        validation.execute();
    }

    @Step("the list is sorted by {string} {string}")
    public void theListIsSorted(String fieldExpression, String order) {
        boolean desc = "desc".equalsIgnoreCase(order);
        List<Object> items = getRequiredStepData(RESULT_LIST);

        assertTrue(isTheListSorted(items, desc, fieldExpression));
    }

}
