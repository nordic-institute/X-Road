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
package ee.ria.xroad.common.identifier;

import java.io.Serializable;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang3.StringUtils;


/**
 * Base class for global identifiers in the XROAD system.
 */
@XmlJavaTypeAdapter(IdentifierTypeConverter.GenericXroadIdAdapter.class)
public abstract class XroadId implements Serializable {

    private final XroadObjectType type;
    private final String xRoadInstance;

    private Long id; // used for references in database

    XroadId() {
        this(null, null);
    }

    XroadId(XroadObjectType type, String xRoadInstance) {
        this.type = type;
        this.xRoadInstance = xRoadInstance;
    }

    Long getId() {
        return id;
    }

    /** Returns type of the object for this identifier.
     * @return XroadObjectType
     */
    public XroadObjectType getObjectType() {
        return type;
    }

    /**
     * Returns code of the XROAD instance.
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
        return type.name() + ":" + toShortString();
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
