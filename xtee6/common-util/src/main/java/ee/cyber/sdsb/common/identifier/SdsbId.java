package ee.cyber.sdsb.common.identifier;

import java.io.Serializable;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang3.StringUtils;


/**
 * Base class for global identifiers in the SDSB system.
 */
@XmlJavaTypeAdapter(IdentifierTypeConverter.GenericSdsbIdAdapter.class)
public abstract class SdsbId implements Serializable {

    private final SdsbObjectType type;
    private final String sdsbInstance;

    SdsbId(SdsbObjectType type, String sdsbInstance) {
        this.type = type;
        this.sdsbInstance = sdsbInstance;
    }

    /** Returns type of the object for this identifier. */
    public SdsbObjectType getObjectType() {
        return type;
    }

    /**
     * Returns code of the SDSB instance.
     */
    public String getSdsbInstance() {
        return sdsbInstance;
    }

    @Override
    public boolean equals(Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public String toString() {
        return type.name() + ":" + toShortString();
    }

    /**
     * Returns short string representation of the identifier that is
     * more suitable for user interface usage than output
     * of the toString() method.
     */
    public String toShortString() {
        StringBuilder sb = new StringBuilder();
        if (sdsbInstance != null) {
            sb.append(sdsbInstance);
        }

        for (String part : getFieldsForStringFormat()) {
            if (part != null) {
                if (sb.length() > 0) {
                    sb.append('/');
                }

                sb.append(part);
            }
        }

        return sb.toString();
    }

    /**
     * Returns the fields for string format of this identifier.
     */
    protected abstract String[] getFieldsForStringFormat();

    protected static void validateField(String fieldName, String fieldValue) {
        if (StringUtils.isBlank(fieldValue)) {
            // TODO: Better error?
            throw new IllegalArgumentException(
                    "'" + fieldName + "' must not be blank");
        }
    }
}
