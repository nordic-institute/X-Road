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

package org.niis.xroad.test.framework.core.glue;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.test.framework.core.context.ScenarioExecutionContext;
import org.niis.xroad.test.framework.core.hooks.AfterScenarioHook;
import org.niis.xroad.test.framework.core.hooks.BeforeScenarioHook;

import java.util.Comparator;
import java.util.List;

/**
 * Cucumber hooks glue class that manages before and after scenario hooks.
 */
@Slf4j
@RequiredArgsConstructor
public class HooksGlue {

    private final List<AfterScenarioHook> afterScenarioHooks;
    private final List<BeforeScenarioHook> beforeScenarioHooks;
    private final ScenarioExecutionContext scenarioExecutionContext;

    /**
     * Cucumber does not expose a way to retrieve Scenario variable in the middle of
     * a step,
     * thus we must resort to grabbing it at the start and keeping it.
     */
    @Before(order = -1)
    public void prepareScenarioContext(Scenario scenario) {
        log.trace("Preparing scenario context");
        scenarioExecutionContext.prepare(scenario);
    }

    @Before(order = 1)
    public void beforeScenario() {
        beforeScenarioHooks.stream()
                .sorted(Comparator.comparingInt(BeforeScenarioHook::beforeScenarioOrder))
                .forEach(hook -> hook.before(scenarioExecutionContext));
    }

    @After(order = Integer.MIN_VALUE)
    public void afterScenario() {
        afterScenarioHooks.stream()
                .sorted(Comparator.comparingInt(AfterScenarioHook::afterScenarioOrder))
                .forEach(hook -> hook.after(scenarioExecutionContext));
    }
}
