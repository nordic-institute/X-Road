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
package org.niis.xroad.opmonitor.core;

import ee.ria.xroad.common.util.JsonUtils;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests serialization and deserialization of the type adapter for the
 * securityServerType field.
 */
public class SecurityServerTypeTypeAdapterTest {

    private static final ObjectWriter OBJECT_WRITER = JsonUtils.getObjectWriter();
    private static final ObjectReader OBJECT_READER = JsonUtils.getObjectReader();

    private static final String OK_JSON_CLIENT = "{\"securityServerType\":\"Client\"}";
    private static final String OK_JSON_PRODUCER = "{\"securityServerType\":\"Producer\"}";

    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    static class Type {
        @Getter
        @Setter
        @JsonDeserialize(using = SecurityServerTypeTypeAdapter.class)
        private String securityServerType;
    }

    @Test
    public void okType() throws IOException {
        Type type = OBJECT_READER.forType(Type.class).readValue(OK_JSON_CLIENT);
        assertEquals("Client", type.getSecurityServerType());

        type = OBJECT_READER.forType(Type.class).readValue(OK_JSON_PRODUCER);
        assertEquals("Producer", type.getSecurityServerType());
    }

    @Test
    public void nokType() {
        String nokJson = "{\"securityServerType\":\"UNKNOWN\"}";
        var err = assertThrows(JacksonException.class, () -> OBJECT_READER.forType(Type.class).readValue(nokJson));
        assertTrue(err.getOriginalMessage().contains("Invalid value of securityServerType"));
    }

    @Test
    public void serialize() throws JacksonException {
        Type type = new Type("Client");
        String json = OBJECT_WRITER.writeValueAsString(type);
        assertEquals(OK_JSON_CLIENT, json);

        type = new Type("Producer");
        json = OBJECT_WRITER.writeValueAsString(type);
        assertEquals(OK_JSON_PRODUCER, json);
    }
}
