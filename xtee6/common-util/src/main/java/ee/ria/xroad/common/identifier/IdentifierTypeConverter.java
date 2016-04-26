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

import javax.xml.bind.annotation.adapters.XmlAdapter;

import ee.ria.xroad.common.CodedException;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;

/**
 * Adapter class for converting between DTO and XML identifier types.
 */
final class IdentifierTypeConverter {

    private IdentifierTypeConverter() {
    }

    // -- Type conversion methods ---------------------------------------------

    static XroadClientIdentifierType printClientId(ClientId v) {
        XroadClientIdentifierType type = new XroadClientIdentifierType();
        type.setObjectType(v.getObjectType());
        type.setXRoadInstance(v.getXRoadInstance());
        type.setMemberClass(v.getMemberClass());
        type.setMemberCode(v.getMemberCode());
        type.setSubsystemCode(v.getSubsystemCode());
        return type;
    }

    static XroadServiceIdentifierType printServiceId(ServiceId v) {
        XroadServiceIdentifierType type = new XroadServiceIdentifierType();
        type.setObjectType(v.getObjectType());
        type.setXRoadInstance(v.getXRoadInstance());
        type.setMemberClass(v.getMemberClass());
        type.setMemberCode(v.getMemberCode());
        type.setSubsystemCode(v.getSubsystemCode());
        type.setServiceCode(v.getServiceCode());
        type.setServiceVersion(v.getServiceVersion());
        return type;
    }

    static XroadSecurityCategoryIdentifierType printSecurityCategoryId(
            SecurityCategoryId v) {
        XroadSecurityCategoryIdentifierType type =
                new XroadSecurityCategoryIdentifierType();
        type.setObjectType(v.getObjectType());
        type.setXRoadInstance(v.getXRoadInstance());
        type.setSecurityCategoryCode(v.getCategoryCode());
        return type;
    }

    static XroadCentralServiceIdentifierType printCentralServiceId(
            CentralServiceId v) {
        XroadCentralServiceIdentifierType type =
                new XroadCentralServiceIdentifierType();
        type.setObjectType(v.getObjectType());
        type.setXRoadInstance(v.getXRoadInstance());
        type.setServiceCode(v.getServiceCode());
        return type;
    }

    static XroadSecurityServerIdentifierType printSecurityServerId(
            SecurityServerId v) {
        XroadSecurityServerIdentifierType type =
                new XroadSecurityServerIdentifierType();
        type.setObjectType(v.getObjectType());
        type.setXRoadInstance(v.getXRoadInstance());
        type.setMemberClass(v.getMemberClass());
        type.setMemberCode(v.getMemberCode());
        type.setServerCode(v.getServerCode());
        return type;
    }

    static XroadGlobalGroupIdentifierType printGlobalGroupId(GlobalGroupId v) {
        XroadGlobalGroupIdentifierType type =
                new XroadGlobalGroupIdentifierType();
        type.setObjectType(v.getObjectType());
        type.setXRoadInstance(v.getXRoadInstance());
        type.setGroupCode(v.getGroupCode());
        return type;
    }

    static XroadLocalGroupIdentifierType printLocalGroupId(LocalGroupId v) {
        XroadLocalGroupIdentifierType type = new XroadLocalGroupIdentifierType();
        type.setObjectType(v.getObjectType());
        type.setXRoadInstance(v.getXRoadInstance());
        type.setGroupCode(v.getGroupCode());
        return type;
    }

    static ClientId parseClientId(XroadIdentifierType v) {
        return ClientId.create(v.getXRoadInstance(), v.getMemberClass(),
                v.getMemberCode(), v.getSubsystemCode());
    }

    static ServiceId parseServiceId(XroadIdentifierType v) {
        return ServiceId.create(v.getXRoadInstance(),
                v.getMemberClass(), v.getMemberCode(),
                v.getSubsystemCode(), v.getServiceCode(),
                v.getServiceVersion());
    }

    static SecurityCategoryId parseSecurityCategoryId(XroadIdentifierType v) {
        return SecurityCategoryId.create(v.getXRoadInstance(),
                v.getSecurityCategoryCode());
    }

    static CentralServiceId parseCentralServiceId(XroadIdentifierType v) {
        return CentralServiceId.create(v.getXRoadInstance(),
                v.getServiceCode());
    }

    static SecurityServerId parseSecurityServerId(XroadIdentifierType v) {
        return SecurityServerId.create(v.getXRoadInstance(),
                v.getMemberClass(), v.getMemberCode(), v.getServerCode());
    }

    static GlobalGroupId parseGlobalGroupId(XroadIdentifierType v) {
        return GlobalGroupId.create(v.getXRoadInstance(), v.getGroupCode());
    }

    static LocalGroupId parseLocalGroupId(XroadIdentifierType v) {
        return LocalGroupId.create(v.getGroupCode());
    }

    // -- Identifier-specific adapter classes ---------------------------------

    static class ClientIdAdapter
        extends XmlAdapter<XroadClientIdentifierType, ClientId> {

        @Override
        public XroadClientIdentifierType marshal(ClientId v)
                throws Exception {
            return v == null ? null : printClientId(v);
        }

        @Override
        public ClientId unmarshal(XroadClientIdentifierType v)
                throws Exception {
            return v == null ? null : parseClientId(v);
        }
    }

    static class ServiceIdAdapter
        extends XmlAdapter<XroadServiceIdentifierType, ServiceId> {

        @Override
        public XroadServiceIdentifierType marshal(ServiceId v)
                throws Exception {
            return v == null ? null : printServiceId(v);
        }

        @Override
        public ServiceId unmarshal(XroadServiceIdentifierType v)
                throws Exception {
            if (v != null) {
                switch (v.getObjectType()) {
                    case SERVICE:
                        return parseServiceId(v);
                    case CENTRALSERVICE:
                        return parseCentralServiceId(v);
                    default:
                        return null;
                }
            } else {
                return null;
            }
        }
    }

    static class SecurityCategoryIdAdapter
        extends XmlAdapter<
            XroadSecurityCategoryIdentifierType, SecurityCategoryId> {

        @Override
        public XroadSecurityCategoryIdentifierType marshal(SecurityCategoryId v)
                throws Exception {
            return v == null ? null : printSecurityCategoryId(v);
        }

        @Override
        public SecurityCategoryId unmarshal(
                XroadSecurityCategoryIdentifierType v) throws Exception {
            return v == null ? null : parseSecurityCategoryId(v);
        }
    }

    static class CentralServiceIdAdapter
        extends XmlAdapter<
            XroadCentralServiceIdentifierType, CentralServiceId> {

        @Override
        public XroadCentralServiceIdentifierType marshal(CentralServiceId v)
                throws Exception {
            return v == null ? null : printCentralServiceId(v);
        }

        @Override
        public CentralServiceId unmarshal(XroadCentralServiceIdentifierType v)
                throws Exception {
            return v == null ? null : parseCentralServiceId(v);
        }
    }

    static class SecurityServerIdAdapter
        extends XmlAdapter<
            XroadSecurityServerIdentifierType, SecurityServerId> {

        @Override
        public XroadSecurityServerIdentifierType marshal(SecurityServerId v)
                throws Exception {
            return v == null ? null : printSecurityServerId(v);
        }

        @Override
        public SecurityServerId unmarshal(XroadSecurityServerIdentifierType v)
                throws Exception {
            return v == null ? null : parseSecurityServerId(v);
        }
    }

    static class GlobalGroupIdAdapter
        extends XmlAdapter<XroadGlobalGroupIdentifierType, GlobalGroupId> {

        @Override
        public XroadGlobalGroupIdentifierType marshal(GlobalGroupId v)
                throws Exception {
            return v == null ? null : printGlobalGroupId(v);
        }

        @Override
        public GlobalGroupId unmarshal(XroadGlobalGroupIdentifierType v)
                throws Exception {
            return v == null ? null : parseGlobalGroupId(v);
        }
    }

    static class LocalGroupIdAdapter
        extends XmlAdapter<XroadLocalGroupIdentifierType, LocalGroupId> {

        @Override
        public XroadLocalGroupIdentifierType marshal(LocalGroupId v)
                throws Exception {
            return v == null ? null : printLocalGroupId(v);
        }

        @Override
        public LocalGroupId unmarshal(XroadLocalGroupIdentifierType v)
                throws Exception {
            return v == null ? null : parseLocalGroupId(v);
        }
    }

    static class GenericXroadIdAdapter
        extends XmlAdapter<XroadIdentifierType, XroadId> {

        @Override
        public XroadIdentifierType marshal(XroadId v) throws Exception {
            if (v == null) {
                return null;
            }

            switch (v.getObjectType()) {
                case MEMBER:
                case SUBSYSTEM:
                    return printClientId((ClientId) v);
                case SERVICE:
                    return printServiceId((ServiceId) v);
                case SERVER:
                    return printSecurityServerId((SecurityServerId) v);
                case CENTRALSERVICE:
                    return printCentralServiceId((CentralServiceId) v);
                case GLOBALGROUP:
                    return printGlobalGroupId((GlobalGroupId) v);
                case LOCALGROUP:
                    return printLocalGroupId((LocalGroupId) v);
                case SECURITYCATEGORY:
                    return printSecurityCategoryId((SecurityCategoryId) v);
                default:
                    throw new CodedException(X_INTERNAL_ERROR,
                            "Unsupported object type: " + v.getObjectType());
            }
        }

        @Override
        public XroadId unmarshal(XroadIdentifierType v) throws Exception {
            if (v == null) {
                return null;
            }

            switch (v.getObjectType()) {
                case MEMBER:
                case SUBSYSTEM:
                    return parseClientId(v);
                case SERVICE:
                    return parseServiceId(v);
                case SERVER:
                    return parseSecurityServerId(v);
                case CENTRALSERVICE:
                    return parseCentralServiceId(v);
                case GLOBALGROUP:
                    return parseGlobalGroupId(v);
                case LOCALGROUP:
                    return parseLocalGroupId(v);
                case SECURITYCATEGORY:
                    return parseSecurityCategoryId(v);
                default:
                    throw new CodedException(X_INTERNAL_ERROR,
                            "Unsupported object type: " + v.getObjectType());
            }
        }
    }
}
