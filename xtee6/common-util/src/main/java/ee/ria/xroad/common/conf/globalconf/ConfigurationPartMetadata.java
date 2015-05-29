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
