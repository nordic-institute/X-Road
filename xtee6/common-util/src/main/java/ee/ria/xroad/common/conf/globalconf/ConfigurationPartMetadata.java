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
package ee.ria.xroad.common.conf.globalconf;

import java.io.IOException;
import java.io.InputStream;

import lombok.Getter;
import lombok.Setter;

import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Represents meta data information for a configuration part.
 * The meta data contains the following information:
 * <ul>
 * <li>content identifier</li>
 * <li>instance identifier</li>
 * <li>expiration date</li>
 * <li>content file name</li>
 * <li>content location</li>
 * </ul>
 */
@Getter
@Setter
public class ConfigurationPartMetadata {

    private String contentIdentifier;

    private String instanceIdentifier;

    @JsonSerialize(using = JodaDateSerializer.class)
    @JsonDeserialize(using = JodaDateDeserializer.class)
    private DateTime expirationDate;

    private String contentFileName;

    private String contentLocation;

    // ------------------------------------------------------------------------

    /**
     * @return the metadata as byte array
     * @throws Exception if an error occurs while serializing the data
     */
    public byte[] toByteArray() throws Exception {
        return new ObjectMapper().writeValueAsBytes(this);
    }

    /**
     * Reads the meta data from input stream.
     * @param in the input stream
     * @return the meta data
     * @throws Exception if an error occurs while deserializing the data
     */
    public static ConfigurationPartMetadata read(InputStream in)
            throws Exception {
        return new ObjectMapper().readValue(in,
                ConfigurationPartMetadata.class);
    }

    static class JodaDateSerializer extends JsonSerializer<DateTime> {

        @Override
        public void serialize(DateTime value, JsonGenerator gen,
                SerializerProvider serializerProvider) throws IOException {
            gen.writeString(value.toString());
        }
    }

    static class JodaDateDeserializer extends JsonDeserializer<DateTime> {

        @Override
        public DateTime deserialize(JsonParser parser,
                DeserializationContext deserializationContext)
                        throws IOException {
            return ISODateTimeFormat.dateTimeParser().parseDateTime(
                    parser.getText());
        }
    }
}
