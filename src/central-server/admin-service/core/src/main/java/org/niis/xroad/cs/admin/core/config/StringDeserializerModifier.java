/*
 * The MIT License
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.cs.admin.core.config;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.ValueDeserializerModifier;
import tools.jackson.databind.deser.jdk.StringDeserializer;

/**
 * Modifier for StringDeserializer
 */
public class StringDeserializerModifier extends ValueDeserializerModifier {
    @Override
    public ValueDeserializer<?> modifyDeserializer(DeserializationConfig config, BeanDescription.Supplier beanDesc,
                                                   ValueDeserializer<?> deserializer) {
        if (deserializer instanceof StringDeserializer) {
            deserializer = new StringTrimmerDeserializer();
        }
        return super.modifyDeserializer(config, beanDesc, deserializer);
    }

    /**
     * Extends the standard Jackson StringDeserializer by trimming
     * the deserialized JSON property string value
     */
    public static class StringTrimmerDeserializer extends StringDeserializer {
        @Override
        public String deserialize(JsonParser jsonParser, DeserializationContext context) throws JacksonException {
            String deserializedString = super.deserialize(jsonParser, context);
            return (deserializedString == null) ? null : deserializedString.trim();
        }
    }
}
