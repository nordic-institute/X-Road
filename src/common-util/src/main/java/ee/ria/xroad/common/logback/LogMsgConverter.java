/**
 * The MIT License
 *
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
package ee.ria.xroad.common.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.CompositeConverter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.io.CharacterEscapes;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import java.io.UncheckedIOException;

public final class LogMsgConverter extends CompositeConverter<ILoggingEvent> {

    private ObjectWriter jsonWriter;

    // should the converter keep "normal" white space like \n and \t
    private boolean keepNormalWhiteSpace = true;

    private static final class CustomCharEscapes extends CharacterEscapes {

        private static final CustomCharEscapes INSTANCE = new CustomCharEscapes();
        private static final int[] ESCAPES;

        static {
            final int[] escapes = standardAsciiEscapesForJSON();
            escapes['\n'] = CharacterEscapes.ESCAPE_NONE;
            escapes['\t'] = CharacterEscapes.ESCAPE_NONE;
            ESCAPES = escapes;
        }

        private CustomCharEscapes() {
            // singleton
        }

        @Override
        public int[] getEscapeCodesForAscii() {
            return ESCAPES;
        }

        @Override
        public SerializableString getEscapeSequence(int ch) {
            return null;
        }
    }

    @Override
    public void start() {
        final String option = getFirstOption();
        if ("false".equalsIgnoreCase(option)) {
            keepNormalWhiteSpace = false;
        }
        final ObjectMapper mapper = new ObjectMapper();
        ObjectWriter tmp = mapper.writer().with(JsonWriteFeature.ESCAPE_NON_ASCII);
        if (keepNormalWhiteSpace) {
            jsonWriter = tmp.with(CustomCharEscapes.INSTANCE);
        } else {
            jsonWriter = tmp;
        }
        super.start();
    }

    @Override
    protected String transform(ILoggingEvent event, String in) {
        try {
            return jsonWriter.writeValueAsString(in == null ? "" : in.trim());
        } catch (JsonProcessingException e) {
            //should not really happen
            throw new UncheckedIOException(e);
        }
    }
}
