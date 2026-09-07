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
package org.niis.xroad.cs.test.api;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.DisableParentConfigurationParameters;
import org.junit.platform.suite.api.ExcludeTags;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

/**
 * Phase 1 of the phased Central Server API test suite. Runs all non-destructive tests concurrently.
 * Executes before {@link CsApiDestructiveSuite} to guarantee that service-state mutations cannot
 * interfere with parallel tests still in flight.
 */
@Suite(failIfNoTests = false)
@SelectPackages("org.niis.xroad.cs.test.api")
@ExcludeTags("destructive")
@DisableParentConfigurationParameters
@ConfigurationParameter(key = "junit.jupiter.execution.parallel.enabled", value = "true")
@ConfigurationParameter(key = "junit.jupiter.execution.parallel.mode.default", value = "concurrent")
@ConfigurationParameter(key = "junit.jupiter.execution.parallel.mode.classes.default", value = "concurrent")
@ConfigurationParameter(key = "junit.jupiter.execution.parallel.config.strategy", value = "fixed")
@ConfigurationParameter(key = "junit.jupiter.execution.parallel.config.fixed.parallelism", value = "4")
@ConfigurationParameter(key = "junit.jupiter.testclass.order.default", value = "org.junit.jupiter.api.ClassOrderer$Random")
@ConfigurationParameter(key = "junit.jupiter.testmethod.order.default", value = "org.junit.jupiter.api.MethodOrderer$Random")
@ConfigurationParameter(key = "junit.jupiter.extensions.autodetection.enabled", value = "true")
class CsApiNonDestructiveSuite {
}
