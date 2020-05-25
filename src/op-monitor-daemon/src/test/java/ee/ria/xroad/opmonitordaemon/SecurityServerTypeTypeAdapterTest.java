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
package ee.ria.xroad.opmonitordaemon;

import ee.ria.xroad.common.util.JsonUtils;

import com.google.gson.Gson;
import com.google.gson.annotations.JsonAdapter;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.junit.Assert.assertEquals;

/**
 * Tests serialization and deserialization of the type adapter for the
 * securityServerType field.
 */
public class SecurityServerTypeTypeAdapterTest {

    private static final Gson GSON = JsonUtils.getSerializer();

    private static final String OK_JSON_CLIENT = "{\"securityServerType\":\"Client\"}";
    private static final String OK_JSON_PRODUCER = "{\"securityServerType\":\"Producer\"}";

    @Rule
    public final ExpectedException expectedException = ExpectedException.none();

    @AllArgsConstructor
    class Type {
        @Getter
        @Setter
        @JsonAdapter(SecurityServerTypeTypeAdapter.class)
        private String securityServerType;
    }

    @Test
    public void okType() {
        Type type = GSON.fromJson(OK_JSON_CLIENT, Type.class);
        assertEquals("Client", type.getSecurityServerType());

        type = GSON.fromJson(OK_JSON_PRODUCER, Type.class);
        assertEquals("Producer", type.getSecurityServerType());
    }

    @Test
    public void nokType() {
        expectedException.expect(RuntimeException.class);
        expectedException.expectMessage("Invalid value of securityServerType");

        String nokJson = "{\"securityServerType\":\"UNKNOWN\"}";
        GSON.fromJson(nokJson, Type.class);
    }

    @Test
    public void serialize() {
        Type type = new Type("Client");
        String json = GSON.toJson(type);
        assertEquals(OK_JSON_CLIENT, json);

        type = new Type("Producer");
        json = GSON.toJson(type);
        assertEquals(OK_JSON_PRODUCER, json);
    }
}
