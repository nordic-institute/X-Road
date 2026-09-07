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
package org.niis.xroad.confproxy.test;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.DisableParentConfigurationParameters;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/**
 * Entry point of the {@code intTest} task and {@link ConsoleIntTestRunner}: runs every scenario class as
 * one serial suite in ascending class {@code @Order}. All three classes drive the same proxy instance
 * ({@code TEST}) on the same long-lived configuration-proxy stack with no state reset between classes -
 * the instance's signing-key count and configuration state built up in one class is asserted on in the
 * next, exactly as the legacy Cucumber suite's file-ordered scenario execution did (0100 API keys, then
 * 0200 CLI instance management, then 0300 REST instance management on the instance 0200 created).
 */
@Suite(failIfNoTests = false)
@SelectClasses({
        ConfProxyApiKeyManagementIntTest.class,
        ConfProxyInstanceConfigurationIntTest.class,
        ConfProxyRestApiIntTest.class
})
@DisableParentConfigurationParameters
@ConfigurationParameter(key = "junit.jupiter.testclass.order.default", value = "org.junit.jupiter.api.ClassOrderer$OrderAnnotation")
@ConfigurationParameter(key = "junit.jupiter.extensions.autodetection.enabled", value = "true")
public class ConfProxyIntTestSuite {
}
