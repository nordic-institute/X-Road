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
