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

import java.lang.reflect.Type;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import ee.ria.xroad.common.identifier.ClientId;

/**
 * This class contains various json related utility methods.
 */
public final class JsonUtils {

    private JsonUtils() {
    }

    /**
     * Get Gson with custom serializer.
     * @return Gson instance with custom serializer.
     */
    public static Gson getSerializer() {
        GsonBuilder builder = new GsonBuilder();
        builder.disableHtmlEscaping();
        builder.registerTypeAdapter(ClientId.class, new ClientIdSerializer());

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
}
