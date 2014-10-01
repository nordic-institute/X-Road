package ee.cyber.sdsb.common.securelog.archive;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;

import ee.cyber.sdsb.common.securelog.AbstractLogRecord;

class JsonUtils {

    private static ObjectMapper objectMapper;

    static ObjectMapper getObjectMapper() {
        if (objectMapper == null) {
            objectMapper = createObjectMapper();
        }

        return objectMapper;
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();

        objectMapper.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);

        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        objectMapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        objectMapper.enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
        objectMapper.enable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES);

        objectMapper.setVisibilityChecker(
                objectMapper.getSerializationConfig().
                getDefaultVisibilityChecker().
                withFieldVisibility(JsonAutoDetect.Visibility.ANY).
                withGetterVisibility(JsonAutoDetect.Visibility.NONE).
                withSetterVisibility(JsonAutoDetect.Visibility.NONE));

        objectMapper.registerModule(new MixInModule());

        return objectMapper;
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
