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
package org.niis.xroad.e2e;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.DisableParentConfigurationParameters;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

/**
 * Runs the entire e2e test tier as one ordered suite against the single shared aux/ss0/ss1 stack. Unlike
 * the SS/CS api-test tiers, there is no destructive/non-destructive split here: the scenarios are
 * state-coupled (see the {@code Order} values on the individual test classes) and always run serially,
 * one class at a time, in ascending {@code @Order}.
 *
 * <p>This is the entry point the {@code e2eTest} Gradle task and {@link ConsoleE2ETestRunner} select by
 * default. Both also support running an individual test class/method directly (bypassing this suite)
 * for IDE-friendly single-scenario runs.
 */
@Suite(failIfNoTests = false)
@SelectPackages("org.niis.xroad.e2e")
@DisableParentConfigurationParameters
@ConfigurationParameter(key = "junit.jupiter.testclass.order.default", value = "org.junit.jupiter.api.ClassOrderer$OrderAnnotation")
@ConfigurationParameter(key = "junit.jupiter.extensions.autodetection.enabled", value = "true")
public class E2eSuite {
}
