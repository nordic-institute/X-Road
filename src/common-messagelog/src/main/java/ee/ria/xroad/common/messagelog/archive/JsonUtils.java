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
