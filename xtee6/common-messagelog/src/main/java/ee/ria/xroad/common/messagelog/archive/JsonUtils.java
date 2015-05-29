package ee.ria.xroad.common.messagelog.archive;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;

import ee.ria.xroad.common.messagelog.AbstractLogRecord;

final class JsonUtils {

    private static ObjectMapper objectMapper;

    private JsonUtils() {
    }

    static ObjectMapper getObjectMapper() {
        if (objectMapper == null) {
            objectMapper = createObjectMapper();
        }

        return objectMapper;
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper newObjectMapper = new ObjectMapper();

        newObjectMapper.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);

        newObjectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        newObjectMapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        newObjectMapper.enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
        newObjectMapper.enable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES);

        newObjectMapper.setVisibilityChecker(
                newObjectMapper.getSerializationConfig().
                getDefaultVisibilityChecker().
                withFieldVisibility(JsonAutoDetect.Visibility.ANY).
                withGetterVisibility(JsonAutoDetect.Visibility.NONE).
                withSetterVisibility(JsonAutoDetect.Visibility.NONE));

        newObjectMapper.registerModule(new MixInModule());

        return newObjectMapper;
    }

    // We use mix-in class because we do not want to add annotations
    // directly to AbstractLogRecord.
    @JsonIgnoreProperties({ "archived", "signatureHash" })
    private static class AbstractLogRecordMixIn {
    }

    private static class MixInModule extends SimpleModule {

        @Override
        public void setupModule(SetupContext context) {
            context.setMixInAnnotations(
                    AbstractLogRecord.class, AbstractLogRecordMixIn.class);
        }
    }
}
