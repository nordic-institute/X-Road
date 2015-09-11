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
