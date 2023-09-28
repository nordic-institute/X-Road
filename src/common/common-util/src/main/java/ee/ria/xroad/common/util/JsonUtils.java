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
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;

/**
 * This class contains various json related utility methods.
 */
public final class JsonUtils {

    private static final ObjectMapper OBJECT_MAPPER;
    private static final ObjectMapper OBJECT_MAPPER_WITH_NULLS;

    static {
        ObjectMapper objectMapperWithNulls = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(ClientId.class, new ClientIdSerializer());
        objectMapperWithNulls.registerModule(module);
        objectMapperWithNulls.registerModule(new JavaTimeModule());
        objectMapperWithNulls.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapperWithNulls.disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
        objectMapperWithNulls.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        OBJECT_MAPPER_WITH_NULLS = objectMapperWithNulls;

        ObjectMapper objectMapperWithoutNulls = objectMapperWithNulls.copy();
        objectMapperWithoutNulls.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        OBJECT_MAPPER = objectMapperWithoutNulls;
    }

    private JsonUtils() {
    }

    /**
     * <strong>Get a copy of the static {@link #OBJECT_MAPPER} instance</strong> e.g. when you need to provide it for
     * some other context such as MappingJackson2HttpMessageConverter. For basic de/serialization needs,
     * please use {@link #getObjectReader()}, {@link #getObjectWriter()} or {@link #getObjectWriter(boolean)} instead
     *
     * @return Jackson ObjectMapper instance with custom deserializer.
     */
    public static ObjectMapper getObjectMapperCopy() {
        return OBJECT_MAPPER.copy();
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
                SerializerProvider serializers) throws IOException {
            gen.writeStartObject();
            gen.writeStringField("xRoadInstance", value.getXRoadInstance());
            gen.writeStringField("memberClass", value.getMemberClass());
            gen.writeStringField("memberCode", value.getMemberCode());

            if (value.getSubsystemCode() != null) {
                gen.writeStringField("subsystemCode", value.getSubsystemCode());
            }
            gen.writeEndObject();
        }
    }
}
