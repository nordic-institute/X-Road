package ee.ria.xroad.common.message;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlValue;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.ToString;

import ee.ria.xroad.common.CodedException;

import static ee.ria.xroad.common.ErrorCodes.X_INVALID_PROTOCOL_VERSION;

/**
 * Represents the protocolVersion header field value.
 */
@Getter
@NoArgsConstructor
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@ToString
public class ProtocolVersion implements ValidatableField {

    // Holds the currently hard coded protocol version value.
    private static final String CURRENT_VERSION_PREFIX = "4.";

    @XmlValue
    protected String version = CURRENT_VERSION_PREFIX + "0";

    /**
     * Constructs new instance and verifies that the version is supported.
     * @param v the version.
     * @throws Exception if the version is invalid
     */
    public ProtocolVersion(String v) throws Exception {
        setVersion(v);
    }

    /**
     * Sets the protocol version.
     * @param v the version
     * @throws Exception (CodedException) with error code
     * 'InvalidProtocolVersion' if the protocol version is not supported.
     */
    public void setVersion(@NonNull String v) throws Exception {
        this.version = v;
        validate();
    }

    @Override
    public void validate() throws Exception {
        // Since the current requirements do not specify semantics for the
        // protocol version, we simply check if the version starts with
        // a predefined string.
        if (!version.startsWith(CURRENT_VERSION_PREFIX)) {
            throw new CodedException(X_INVALID_PROTOCOL_VERSION,
                    "Invalid protocol version (supported: %s, provided: %s)",
                    CURRENT_VERSION_PREFIX + 'x', version);
        }
    }
}
