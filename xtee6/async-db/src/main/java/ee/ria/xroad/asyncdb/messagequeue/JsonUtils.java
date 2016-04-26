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
package ee.ria.xroad.asyncdb.messagequeue;

import java.lang.reflect.Type;
import java.util.Date;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

final class JsonUtils {
    private JsonUtils() {
    }

    static Gson getSerializer() {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(Date.class, new DateSerializer());
        return builder.create();
    }

    static String getStringPropertyValue(JsonObject jsonObject,
            String memberName) {
        JsonElement jsonElement = jsonObject.get(memberName);

        if (jsonElement == null) {
            return null;
        }
        return jsonElement.getAsString();
    }

    static Date getDatePropertyValue(JsonObject jsonObject,
            String memberName) {
        JsonElement jsonElement = jsonObject.get(memberName);

        if (jsonElement == null) {
            return null;
        }

        return new Date(jsonElement.getAsLong());
    }

    static int getIntPropertyValue(JsonObject jsonObject,
            String memberName) {
        JsonElement jsonElement = jsonObject.get(memberName);

        if (jsonElement == null) {
            return 0;
        }
        return jsonElement.getAsInt();
    }

    static boolean getBooleanPropertyValue(JsonObject jsonObject,
            String memberName) {
        JsonElement jsonElement = jsonObject.get(memberName);

        return jsonElement != null && jsonElement.getAsBoolean();
    }


    private static class DateSerializer implements JsonSerializer<Date> {
        @Override
        public JsonElement serialize(Date src, Type typeOfSrc,
                JsonSerializationContext context) {
            return new JsonPrimitive(src.getTime());
        }
    }
}
