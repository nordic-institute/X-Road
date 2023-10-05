/*
 * The MIT License
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
package ee.ria.xroad.common.identifier;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.vavr.control.Option;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.Objects;

import static ee.ria.xroad.common.identifier.XRoadObjectType.MEMBER;
import static ee.ria.xroad.common.identifier.XRoadObjectType.SUBSYSTEM;
import static ee.ria.xroad.common.util.Validation.validateArgument;
import static ee.ria.xroad.common.util.Validation.validateOptionalArgument;

/**
 * Client ID.
 */
public interface ClientId extends XRoadId {

    String getMemberClass();

    String getMemberCode();

    String getSubsystemCode();

    ClientId getMemberId();

    @Override
    default String[] getFieldsForStringFormat() {
        return new String[]{getMemberClass(), getMemberCode(), getSubsystemCode()};
    }

    // todo: move to a proper location
    @XmlJavaTypeAdapter(IdentifierTypeConverter.ClientIdAdapter.class)
    final class Conf extends XRoadId.Conf implements ClientId {

        private final String memberClass;
        private final String memberCode;
        private final String subsystemCode;


        Conf() { // required by Hibernate
            this(null, null, null, null);
        }

        private Conf(String xRoadInstance, String memberClass,
                     String memberCode, String subsystemCode) {
            super(subsystemCode == null ? MEMBER : SUBSYSTEM, xRoadInstance);

            this.memberClass = memberClass;
            this.memberCode = memberCode;
            this.subsystemCode = subsystemCode;
        }

        public static ClientId.Conf ensure(ClientId identifier) {
            validateArgument("identifier", identifier);
            return Option.of(identifier)
                    .filter(ClientId.Conf.class::isInstance)
                    .map(ClientId.Conf.class::cast)
                    .getOrElse(() -> new ClientId.Conf(identifier.getXRoadInstance(),
                            identifier.getMemberClass(),
                            identifier.getMemberCode(),
                            identifier.getSubsystemCode()));
        }

        /**
         * Returns the member class of the client.
         *
         * @return String
         */
        public String getMemberClass() {
            return memberClass;
        }

        /**
         * Returns the member code of the client.
         *
         * @return String
         */
        public String getMemberCode() {
            return memberCode;
        }

        /**
         * Returns subsystem code, if present, or null otherwise.
         *
         * @return String or null
         */
        public String getSubsystemCode() {
            return subsystemCode;
        }

        /**
         * Returns {@code this} if this id already is a member id, or ClientId
         * of this subsystem's member if this id is a subsystem id
         */
        @JsonIgnore
        public ClientId.Conf getMemberId() {
            if (getSubsystemCode() == null) {
                return this;
            } else {
                return ClientId.Conf.create(this.getXRoadInstance(),
                        this.getMemberClass(),
                        this.getMemberCode());
            }
        }

        @Override
        public boolean equals(Object other) {
            return ClientId.equals(this, other);
        }

        @Override
        public int hashCode() {
            return ClientId.hashCode(this);
        }

        /**
         * Factory method for creating a new Subsystem.
         *
         * @param xRoadInstance instance of the new subsystem
         * @param memberClass   member class of the new subsystem
         * @param memberCode    member code of the new subsystem
         * @param subsystemCode subsystem code of the new subsystem
         * @return ClientId
         */
        public static ClientId.Conf create(String xRoadInstance,
                                           String memberClass,
                                           String memberCode,
                                           String subsystemCode) {
            validateArgument("xRoadInstance", xRoadInstance);
            validateArgument("memberClass", memberClass);
            validateArgument("memberCode", memberCode);
            validateOptionalArgument("subsystemCode", subsystemCode);

            return new ClientId.Conf(
                    xRoadInstance, memberClass, memberCode, subsystemCode);
        }

        /**
         * Factory method for creating a new ClientId.
         *
         * @param xRoadInstance instance of the new client
         * @param memberClass   member class of the new client
         * @param memberCode    member code of the new client
         * @return ClientId
         */
        public static ClientId.Conf create(String xRoadInstance,
                                           String memberClass,
                                           String memberCode) {
            return create(xRoadInstance, memberClass, memberCode, null);
        }

    }

    /**
     * Determines whether the given member is a subsystem of this client.
     *
     * @param member ID of the potential subsystem
     * @return true if and only if this object represents a subsystem and
     * the argument represents a member and this object contains the member
     * class and code
     */
    default boolean subsystemContainsMember(ClientId member) {
        if (member != null
                && getObjectType() == XRoadObjectType.SUBSYSTEM
                && member.getObjectType() == XRoadObjectType.MEMBER) {
            return memberEquals(member);
        }

        return false;
    }

    /**
     * Determines if another client is equal to this one. This comparison
     * ignores subsystem part of the identifier.
     * Thus, SUBSYSTEM:XX/YY/ZZ/WW is considered equal to
     * SUBSYSTEM:XX/YY/ZZ/TT and MEMBER:XX/YY/ZZ.
     *
     * @param other the ID of the other client
     * @return true, if two identifiers, this and other, refer to the same
     * X-Road member
     */
    default boolean memberEquals(ClientId other) {
        if (other == null) {
            return false;
        }

        return getXRoadInstance().equals(other.getXRoadInstance())
                && getMemberClass().equals(other.getMemberClass())
                && getMemberCode().equals(other.getMemberCode());
    }

    static boolean equals(ClientId self, Object other) {
        if (self == other) return true;
        if (!(other instanceof ClientId identifier)) return false;
        if (!XRoadId.equals(self, other)) return false;
        if (!Objects.equals(self.getMemberClass(), identifier.getMemberClass())) return false;
        if (!Objects.equals(self.getMemberCode(), identifier.getMemberCode())) return false;
        return Objects.equals(self.getSubsystemCode(), identifier.getSubsystemCode());
    }

    static int hashCode(ClientId self) {
        return new HashCodeBuilder()
                .appendSuper(XRoadId.hashCode(self))
                .append(self.getMemberClass())
                .append(self.getMemberCode())
                .append(self.getSubsystemCode())
                .build();
    }

}
