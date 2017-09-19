/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package ee.ria.xroad.common.util;

import com.google.gson.Gson;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static ee.ria.xroad.common.util.JsonUtils.Exclude;

/**
 * Unit tests for {@link ee.ria.xroad.common.util.JsonUtils}
 */
public class JsonUtilsTest {

    private static final Gson GSON = JsonUtils.getSerializer();

    @RequiredArgsConstructor
    private class Foo {
        @Getter
        private final int a;
        @Exclude
        @Getter
        private final int b;
    }

    /**
     * Ensure excluded field is not serialized.
     */
    @Test
    public void testIgnoresTabsInContentType() {
        assertEquals("{\"a\":100}", GSON.toJson(new Foo(100, 200)));
    }
}
