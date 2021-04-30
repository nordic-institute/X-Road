/**
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
package ee.ria.xroad.common.util;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.junit.Test;

import java.time.OffsetDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * Unit tests for {@link ee.ria.xroad.common.util.JsonUtils}
 */
public class JsonUtilsTest {

    private static final ObjectWriter OBJECT_WRITER = JsonUtils.getObjectWriter(true);
    private static final ObjectReader OBJECT_READER = JsonUtils.getObjectReader();

    @Value
    @NoArgsConstructor(force = true, access = AccessLevel.PRIVATE)
    @RequiredArgsConstructor
    public static class Foo {
        int a;
        @JsonIgnore
        int b;

        OffsetDateTime offsetDateTime;
        OffsetDateTime otherUpdate;
    }

    /**
     * Ensure excluded field is not serialized.
     */
    @Test
    public void testIgnoresTabsInContentType() throws Exception {
        final Foo foo = new Foo(100, 200, OffsetDateTime.now(), null);
        final String json = OBJECT_WRITER.writeValueAsString(foo);
        final Foo foo2 = OBJECT_READER.readValue(json, Foo.class);

        assertEquals(foo.a, foo2.a);
        assertEquals(foo.offsetDateTime, foo2.offsetDateTime);
        assertEquals(foo.otherUpdate, foo2.otherUpdate);
        assertNotEquals(foo.b, foo2.b);
    }
}
