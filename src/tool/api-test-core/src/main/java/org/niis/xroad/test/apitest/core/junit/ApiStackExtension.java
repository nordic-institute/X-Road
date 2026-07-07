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
package org.niis.xroad.test.apitest.core.junit;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.niis.xroad.test.apitest.core.config.ApiStackHolder;

/**
 * Injects the shared product stack into test parameters. The stack lifecycle is managed by a
 * product-specific subclass of
 * {@link org.niis.xroad.test.apitest.core.config.AbstractApiStackSessionListener}, which boots it
 * once per JVM launcher session and tears it down at session close. Resolution uses
 * {@code isInstance} so any product's setup and seeder types are matched without product knowledge
 * in core.
 */
public class ApiStackExtension implements ParameterResolver {

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        var type = parameterContext.getParameter().getType();
        return type.isInstance(ApiStackHolder.setup()) || type.isInstance(ApiStackHolder.seeder());
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        var type = parameterContext.getParameter().getType();
        var seeder = ApiStackHolder.seeder();
        if (seeder != null && type.isInstance(seeder)) {
            return seeder;
        }
        var setup = ApiStackHolder.setup();
        if (setup != null && type.isInstance(setup)) {
            return setup;
        }
        throw new ParameterResolutionException(
                "No started stack object matches parameter type " + type
                        + " (stack not started or holder half-initialized)");
    }
}
