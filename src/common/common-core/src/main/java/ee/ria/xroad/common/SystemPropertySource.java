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
package ee.ria.xroad.common;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * This is a temporary SystemPropertySource class that will be removed once the SystemProperties class is refactored.
 * TODO xroad8 should be removed
 */
@Deprecated(forRemoval = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SystemPropertySource {
    private static final SystemPropertiesPropertyResolver SYSTEM_PROPERTIES_PROPERTY_RESOLVER = new SystemPropertiesPropertyResolver();

    @Setter
    private static PropertyResolver propertyResolver;


    public static PropertyResolver getPropertyResolver() {
        if (propertyResolver == null) {
            return SYSTEM_PROPERTIES_PROPERTY_RESOLVER;
        }
        return propertyResolver;
    }


    public static class SystemPropertiesPropertyResolver implements PropertyResolver {
        @Override
        public String getProperty(String key) {
            return System.getProperty(key);
        }

        @Override
        public String getProperty(String key, String defaultValue) {
            return System.getProperty(key, defaultValue);
        }

        @Override
        public <T> T getProperty(String key, Class<T> targetType) {
            throw new UnsupportedOperationException();
        }

    }

    public interface PropertyResolver {
        String getProperty(String key);

        String getProperty(String key, String defaultValue);

        <T> T getProperty(String key, Class<T> targetType);
    }
}
