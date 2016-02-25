package ee.ria.xroad.common.identifier;

import java.io.Serializable;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang3.StringUtils;


/**
 * Base class for global identifiers in the X-Road system.
 */
@XmlJavaTypeAdapter(IdentifierTypeConverter.GenericXRoadIdAdapter.class)
public abstract class XRoadId implements Serializable {

    private final XRoadObjectType type;
    private final String xRoadInstance;

    private Long id; // used for references in database

    XRoadId() {
        this(null, null);
    }

    XRoadId(XRoadObjectType type, String xRoadInstance) {
        this.type = type;
        this.xRoadInstance = xRoadInstance;
    }

    Long getId() {
        return id;
    }

    /** Returns type of the object for this identifier.
     * @return XRoadObjectType
     */
    public XRoadObjectType getObjectType() {
        return type;
    }

    /**
     * Returns code of the X-Road instance.
     * @return String
     */
    public String getXRoadInstance() {
        return xRoadInstance;
    }

    @Override
    public boolean equals(Object obj) {
        // exclude 'id' field, because it is not part of identifier
        // and all identifiers are unique
        return EqualsBuilder.reflectionEquals(this, obj, new String[] {"id"});
    }

    @Override
    public int hashCode() {
        // exclude 'id' field, because it is not part of identifier
        // and all identifiers are unique
        return HashCodeBuilder.reflectionHashCode(this, new String[] {"id"});
    }

    @Override
    public String toString() {
        return type + ":" + toShortString();
    }

    /**
     * Returns short string representation of the identifier that is
     * more suitable for user interface usage than output
     * of the toString() method.
     * @return String
     */
    public String toShortString() {
        StringBuilder sb = new StringBuilder();
        if (xRoadInstance != null) {
            sb.append(xRoadInstance);
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
            throw new IllegalArgumentException(
                    "'" + fieldName + "' must not be blank");
        }
    }

    protected static void validateOptionalField(String fieldName,
            String fieldValue) {
        if (fieldValue != null) {
            validateField(fieldName, fieldValue);
        }
    }
}
