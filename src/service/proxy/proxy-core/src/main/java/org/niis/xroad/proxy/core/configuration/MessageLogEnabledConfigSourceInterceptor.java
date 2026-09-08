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
package org.niis.xroad.proxy.core.configuration;

import io.smallrye.config.ConfigSourceInterceptor;
import io.smallrye.config.ConfigSourceInterceptorContext;
import io.smallrye.config.ConfigValue;

/**
 * {@code @LookupIfProperty(stringValue = "true")} compares the raw configuration string as-is, while
 * {@link ProxyMessageLogProperties#enabled()} converts it through the standard case-insensitive boolean
 * converter. Without this normalization the two can disagree (e.g. {@code enabled: True}), causing the
 * message-log beans to be excluded from the CDI bean archive even though the flag was meant to be enabled.
 */
public class MessageLogEnabledConfigSourceInterceptor implements ConfigSourceInterceptor {

    private static final String PROPERTY_NAME = "xroad.proxy.message-log.enabled";

    @Override
    public ConfigValue getValue(ConfigSourceInterceptorContext context, String name) {
        ConfigValue configValue = context.proceed(name);
        if (configValue == null || configValue.getValue() == null || !PROPERTY_NAME.equals(name)) {
            return configValue;
        }

        String normalized = Boolean.parseBoolean(configValue.getValue()) ? "true" : "false";
        if (normalized.equals(configValue.getValue())) {
            return configValue;
        }
        return configValue.withValue(normalized);
    }
}
