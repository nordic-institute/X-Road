/**
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
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
package ee.ria.xroad.common.identifier;

import ee.ria.xroad.common.util.NoCoverage;

import org.apache.commons.lang3.builder.HashCodeBuilder;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;


/**
 * Base class for global identifiers in the X-Road system.
 */
public interface XRoadId extends Serializable {
    /**
     * Separator char for different types of encoded ids: client id,
     * service id, security server id...
     */
    char ENCODED_ID_SEPARATOR = ':';

    XRoadObjectType getObjectType();

    String getXRoadInstance();

    String[] getFieldsForStringFormat();

    /**
     * Returns as encoded identifier.
     *
     * @return identifier
     */
    default String asEncodedId() {
        return asEncodedId(false);
    }

    /**
     * Returns as encoded ident ifier.
     *
     * @param includeType if true XRoadObjectType is added before identifier itself.
     * @return identifier
     */
    default String asEncodedId(boolean includeType) {
        StringBuilder builder = new StringBuilder();
        if (includeType) {
            builder.append(getObjectType())
                    .append(ENCODED_ID_SEPARATOR);
        }

        return builder
                .append(toShortString(ENCODED_ID_SEPARATOR))
                .toString().trim();
    }

    default String toString(char delimiter) {
        return getObjectType().toString() + delimiter + toShortString(delimiter);
    }

    default String toShortString() {
        return toShortString('/');
    }

    /**
     * Returns short string representation of the identifier that is
     * more suitable for user interface usage than output
     * of the toString() method.
     *
     * @return String
     */
    default String toShortString(char delimiter) {
        StringBuilder sb = new StringBuilder();
        Optional.ofNullable(getXRoadInstance())
                .ifPresent(sb::append);

        for (String part : getFieldsForStringFormat()) {
            if (part != null) {
                if (sb.length() > 0) {
                    sb.append(delimiter);
                }

                sb.append(part);
            }
        }

        return sb.toString();
    }

    // todo: move to a proper location
    @XmlJavaTypeAdapter(IdentifierTypeConverter.GenericXRoadIdAdapter.class)
    abstract class Conf implements XRoadId {

        private Long id; // used for references in database

        private final XRoadObjectType type;
        private final String xRoadInstance;

        Conf() {
            this(null, null);
        }

        Conf(XRoadObjectType type, String xRoadInstance) {
            this.type = type;
            this.xRoadInstance = xRoadInstance;
        }

        /**
         * Returns type of the object for this identifier.
         *
         * @return XRoadObjectType
         */
        public XRoadObjectType getObjectType() {
            return type;
        }

        /**
         * Returns code of the X-Road instance.
         *
         * @return String
         */
        public String getXRoadInstance() {
            return xRoadInstance;
        }

        @Override
        @NoCoverage
        public String toString() {
            return XRoadId.toString(this);
        }

        @Override
        @NoCoverage
        public boolean equals(Object obj) {
            return XRoadId.equals(this, obj);
        }

        @Override
        @NoCoverage
        public int hashCode() {
            return XRoadId.hashCode(this);
        }

    }

    static String toString(XRoadId identifier) {
        return identifier.getObjectType().toString() + ":" + identifier.toShortString();
    }

    static boolean equals(XRoadId self, Object target) {
        if (self == target) return true;
        if (!(target instanceof XRoadId)) return false;
        XRoadId identifier = (XRoadId) target;
        if (self.getObjectType() != identifier.getObjectType()) return false;
        if (!Objects.equals(self.getXRoadInstance(), identifier.getXRoadInstance())) return false;
        return true;
    }

    static int hashCode(XRoadId self) {
        return new HashCodeBuilder()
                .append(self.getObjectType())
                .append(self.getXRoadInstance())
                .build();
    }

}
