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
import com.nortal.test.asserts.ValidationService;
import com.nortal.test.core.services.CucumberScenarioProvider;
import com.nortal.test.core.services.ScenarioContext;
import org.opentest4j.AssertionFailedError;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

/**
 * Base class for all step definitions. Provides convenience methods and most commonly used beans.
 */
@SuppressWarnings("SpringJavaAutowiredMembersInspection")
public abstract class BaseStepDefs {
    @Autowired
    private ScenarioContext scenarioContext;
    @Autowired
    protected CucumberScenarioProvider cucumberScenarioProvider;
    @Autowired
    protected ValidationService validationService;

    protected Assertion equalsAssertion(Object expected, Object actual, String message) {
        return new Assertion.Builder()
                .message(message)
                .expression("=")
                .actualValue(actual)
                .expectedValue(expected)
                .build();
    }

    /**
     * Put a value in scenario context. Value can be accessed through getStepData.
     *
     * @param key   value key. Non-null.
     * @param value value
     */
    protected void putStepData(String key, Object value) {
        scenarioContext.putStepData(key, value);
    }

    /**
     * Get value from scenario context.
     *
     * @param key value key
     * @return value from the context
     */
    protected <T> Optional<T> getStepData(String key) {
        return Optional.ofNullable(scenarioContext.getStepData(key));
    }

    /**
     * Get value from scenario context.
     *
     * @param key value key
     * @return value from the context
     * @throws AssertionFailedError thrown if value is missing
     */
    protected <T> T getRequiredStepData(String key) throws AssertionFailedError {
        return scenarioContext.getRequiredStepData(key);
    }

}
