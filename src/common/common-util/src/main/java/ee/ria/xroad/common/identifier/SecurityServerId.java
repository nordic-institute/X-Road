/*
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

import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.Objects;

import static ee.ria.xroad.common.util.Validation.validateArgument;

/**
 * Security server ID.
 */
public interface SecurityServerId extends XRoadId {

    String getMemberClass();

    String getMemberCode();

    String getServerCode();

    ClientId getOwner();

    default String[] getFieldsForStringFormat() {
        return new String[]{getMemberClass(), getMemberCode(), getServerCode()};
    }

    @XmlJavaTypeAdapter(IdentifierTypeConverter.SecurityServerIdAdapter.class)
    final class Conf extends XRoadId.Conf implements SecurityServerId {
        private final String memberClass;
        private final String memberCode;
        private final String serverCode;

        Conf() { // required by Hibernate
            this(null, null, null, null);
        }

        private Conf(String xRoadInstance,
                     String memberClass, String memberCode, String serverCode) {
            super(XRoadObjectType.SERVER, xRoadInstance);

            this.memberClass = memberClass;
            this.memberCode = memberCode;
            this.serverCode = serverCode;
        }

        /**
         * Returns the owner member class of thesecurity server.
         *
         * @return String
         */
        public String getMemberClass() {
            return memberClass;
        }

        /**
         * Returns the owner member code of the security server.
         *
         * @return String
         */
        public String getMemberCode() {
            return memberCode;
        }

        /**
         * Returns the server code of the security server.
         *
         * @return String
         */
        public String getServerCode() {
            return serverCode;
        }

        /**
         * Returns the client ID of the owner of the security server.
         *
         * @return ClientId
         */
        public ClientId.Conf getOwner() {
            return ClientId.Conf.create(getXRoadInstance(), memberClass, memberCode);
        }

        @Override
        @NoCoverage
        public boolean equals(Object other) {
            return SecurityServerId.equals(this, other);
        }

        @Override
        @NoCoverage
        public int hashCode() {
            return SecurityServerId.hashCode(this);
        }

        /**
         * Factory method for creating a new SecurityServerId.
         *
         * @param xRoadInstance instance of the new security server
         * @param memberClass   class of the new security server owner
         * @param memberCode    code of the new security server owner
         * @param serverCode    code of the new security server
         * @return SecurityServerId
         */
        public static SecurityServerId.Conf create(String xRoadInstance,
                                                   String memberClass,
                                                   String memberCode,
                                                   String serverCode) {
            validateArgument("xRoadInstance", xRoadInstance);
            validateArgument("memberClass", memberClass);
            validateArgument("memberCode", memberCode);
            validateArgument("serverCode", serverCode);
            return new SecurityServerId.Conf(xRoadInstance, memberClass, memberCode,
                    serverCode);
        }

        /**
         * Factory method for creating a new SecurityServerId from ClientId and
         * server code.
         *
         * @param client     ID of the new security server owner
         * @param serverCode code of the new security server
         * @return SecurityServerId
         */
        public static SecurityServerId.Conf create(ClientId client,
                                                   String serverCode) {
            return create(client.getXRoadInstance(), client.getMemberClass(),
                    client.getMemberCode(), serverCode);
        }

    }

    static boolean equals(SecurityServerId self, Object other) {
        if (self == other) return true;
        if (!(other instanceof SecurityServerId identifier)) return false;
        if (!XRoadId.equals(self, other)) return false;
        if (!Objects.equals(self.getMemberClass(), identifier.getMemberClass())) return false;
        if (!Objects.equals(self.getMemberCode(), identifier.getMemberCode())) return false;
        return Objects.equals(self.getServerCode(), identifier.getServerCode());
    }

    static int hashCode(SecurityServerId self) {
        return new HashCodeBuilder()
                .appendSuper(XRoadId.hashCode(self))
                .append(self.getMemberClass())
                .append(self.getMemberCode())
                .append(self.getServerCode())
                .build();
    }

}
