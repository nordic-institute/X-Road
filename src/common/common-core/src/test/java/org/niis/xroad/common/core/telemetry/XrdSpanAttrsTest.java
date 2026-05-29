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
package org.niis.xroad.common.core.telemetry;

import io.opentelemetry.api.common.AttributeKey;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class XrdSpanAttrsTest {

    @Test
    void allKeyStringsAreUnique() throws Exception {
        var keys = collectAllKeys();
        var keyStrings = keys.stream().map(AttributeKey::getKey).toList();
        assertThat(keyStrings).doesNotHaveDuplicates();
    }

    @Test
    void allKeyStringsStartWithXroadPrefix() throws Exception {
        var keys = collectAllKeys();
        for (var key : keys) {
            assertThat(key.getKey())
                    .as("Key '%s' must start with 'xroad.'", key.getKey())
                    .startsWith("xroad.");
        }
    }

    private List<AttributeKey<?>> collectAllKeys() throws Exception {
        List<AttributeKey<?>> result = new ArrayList<>();
        collectKeys(XrdSpanAttrs.class, result);
        return result;
    }

    private void collectKeys(Class<?> clazz, List<AttributeKey<?>> result) throws Exception {
        for (Field field : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())
                    && AttributeKey.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                result.add((AttributeKey<?>) field.get(null));
            }
        }
        for (Class<?> nested : clazz.getDeclaredClasses()) {
            collectKeys(nested, result);
        }
    }
}
