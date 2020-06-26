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

import ee.ria.xroad.common.identifier.ClientId;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Type;

/**
 * This class contains various json related utility methods.
 */
public final class JsonUtils {

    private JsonUtils() {
    }

    /**
     * Get Gson with custom serializer.
     * Default serializer does not serialize nulls
     * @return Gson instance with custom serializer.
     */
    public static Gson getSerializer() {
        return getSerializer(false);
    }

    /**
     * Get Gson with custom serializer.
     * @param serializeNulls if null values should be serialized
     * @return Gson instance with custom serializer.
     */
    public static Gson getSerializer(boolean serializeNulls) {
        GsonBuilder builder = new GsonBuilder();
        builder.disableHtmlEscaping();
        builder.registerTypeAdapter(ClientId.class, new ClientIdSerializer());
        if (serializeNulls) {
            builder.serializeNulls();
        }

        builder.setExclusionStrategies(new ExclusionStrategy() {
            @Override
            public boolean shouldSkipField(FieldAttributes f) {
                return f.getAnnotation(Exclude.class) != null;
            }

            @Override
            public boolean shouldSkipClass(Class<?> clazz) {
                return false;
            }
        });

        return builder.create();
    }


    private static class ClientIdSerializer implements JsonSerializer<ClientId> {
        @Override
        public JsonElement serialize(ClientId src, Type typeOfSrc,
                JsonSerializationContext context) {
            JsonObject o = new JsonObject();
            o.addProperty("xRoadInstance", src.getXRoadInstance());
            o.addProperty("memberClass", src.getMemberClass());
            o.addProperty("memberCode", src.getMemberCode());

            if (src.getSubsystemCode() != null) {
                o.addProperty("subsystemCode", src.getSubsystemCode());
            }

            return o;
        }
    }

    /**
     * Annotation to exclude fields from serialization/deserialization.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Exclude {
    }

}
