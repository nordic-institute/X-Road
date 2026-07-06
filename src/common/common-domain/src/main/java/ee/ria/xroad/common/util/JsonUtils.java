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
package ee.ria.xroad.common.util;

import ee.ria.xroad.common.identifier.ClientId;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * This class contains various json related utility methods.
 */
public final class JsonUtils {

    private static final ObjectMapper OBJECT_MAPPER;
    private static final ObjectMapper OBJECT_MAPPER_WITH_NULLS;

    static {
        SimpleModule module = new SimpleModule();
        module.addSerializer(ClientId.class, new ClientIdSerializer());

        OBJECT_MAPPER_WITH_NULLS = JsonMapper.builder()
                .addModule(module)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build();

        OBJECT_MAPPER = JsonMapper.builder()
                .addModule(module)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .changeDefaultPropertyInclusion(v -> JsonInclude.Value.construct(
                        JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
                .build();
    }

    private JsonUtils() {
    }

    /**
     * Get Jackson ObjectReader with custom deserializer.
     *
     * @return Jackson ObjectReader instance with custom deserializer.
     */
    public static ObjectReader getObjectReader() {
        return OBJECT_MAPPER.reader();
    }

    /**
     * Get Jackson ObjectWriter with custom serializer.
     * Default serializer does not serialize nulls
     *
     * @return Jackson ObjectWriter instance with custom serializer.
     */
    public static ObjectWriter getObjectWriter() {
        return OBJECT_MAPPER.writer();
    }

    /**
     * Get Jackson ObjectWriter with custom serializer.
     *
     * @param serializeNulls if null values should be serialized
     * @return Jackson ObjectWriter instance with custom serializer.
     */
    public static ObjectWriter getObjectWriter(boolean serializeNulls) {
        if (serializeNulls) {
            return OBJECT_MAPPER_WITH_NULLS.writer();
        }
        return OBJECT_MAPPER.writer();
    }

    private static class ClientIdSerializer extends StdSerializer<ClientId> {
        protected ClientIdSerializer() {
            this(null);
        }

        protected ClientIdSerializer(Class<ClientId> t) {
            super(t);
        }

        @Override
        public void serialize(ClientId value, JsonGenerator gen,
                              SerializationContext serializers) {
            gen.writeStartObject();
            gen.writeStringProperty("xRoadInstance", value.getXRoadInstance());
            gen.writeStringProperty("memberClass", value.getMemberClass());
            gen.writeStringProperty("memberCode", value.getMemberCode());

            if (value.getSubsystemCode() != null) {
                gen.writeStringProperty("subsystemCode", value.getSubsystemCode());
            }
            gen.writeEndObject();
        }
    }
}
