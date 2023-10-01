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
package org.niis.xroad.securityserver.restapi.converter;

import org.niis.xroad.securityserver.restapi.config.AbstractFacadeMockingTestContext;
import org.niis.xroad.securityserver.restapi.service.PossibleActionsRuleEngine;
import org.niis.xroad.securityserver.restapi.service.VersionService;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;

/**
 * Base for all converter tests that need some mocked beans in the application context.
 * All converter test classes inheriting this will have a common Spring Application Context
 * therefore drastically reducing the execution time of the converter tests
 *
 * Do not introduce new @MockBean or @SpyBean dependencies in the inherited classes. Doing so will mean Spring
 * creates a different applicationContext for the inherited class and other AbstractServiceTestContext classes,
 * and the performance improvement from using this base class is not realized. If possible, define all mocks and spies
 * in this base class instead.
 *
 * Mocks the usual untestable facades (such as SignerProxyFacade) via {@link AbstractFacadeMockingTestContext}
 */

public abstract class AbstractConverterTestContext extends AbstractFacadeMockingTestContext {
    @MockBean
    VersionService versionService;
    @SpyBean
    PossibleActionsRuleEngine possibleActionsRuleEngine;
    @SpyBean
    KeyConverter keyConverter;
}
