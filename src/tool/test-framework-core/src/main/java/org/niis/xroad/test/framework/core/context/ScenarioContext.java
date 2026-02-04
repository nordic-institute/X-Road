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

package org.niis.xroad.test.framework.core.context;

import io.cucumber.spring.ScenarioScope;
import jakarta.annotation.Nonnull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.junit.jupiter.api.Assertions;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Scenario context. Data is scoped to a particular scenario and is not shared between test suite.
 */
@Data
@NoArgsConstructor
@Component
@ScenarioScope
public class ScenarioContext {

    /**
     * Unique identifier
     */
    @Nonnull
    private String scenarioId = UUID.randomUUID().toString();

    /**
     * Scenario start time
     */
    @NonNull
    private OffsetDateTime scenarioStartTime = OffsetDateTime.now();

    /**
     * Hashmap based step data context. Useful for sharing simple variables between steps.
     */
    @NonNull
    private Map<String, Object> stepData = new HashMap<>();

    /**
     * Get step data from key - value mapping.
     */
    @SuppressWarnings("unchecked")
    public <T> T getStepData(String key) {
        return (T) stepData.get(key);
    }

    /**
     * Get step data from key - value mapping that should be present during this call.
     *
     * @throws org.opentest4j.AssertionFailedError if value is null
     */
    public <T> T getRequiredStepData(String key) {
        T data = getStepData(key);
        Assertions.assertNotNull(data);
        return data;
    }

    /**
     * Puts a value in step data map.
     */
    public void putStepData(String key, Object value) {
        stepData.put(key, value);
    }
}
