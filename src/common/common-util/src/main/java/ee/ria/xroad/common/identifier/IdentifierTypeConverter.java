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

import ee.ria.xroad.common.CodedException;

import jakarta.xml.bind.annotation.adapters.XmlAdapter;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_CLIENT_IDENTIFIER;

/**
 * Adapter class for converting between DTO and XML identifier types.
 */
final class IdentifierTypeConverter {

    private IdentifierTypeConverter() {
    }

    // -- Type conversion methods ---------------------------------------------

    static XRoadClientIdentifierType printClientId(ClientId v) {
        XRoadClientIdentifierType type = new XRoadClientIdentifierType();
        type.setObjectType(v.getObjectType());
        type.setXRoadInstance(v.getXRoadInstance());
        type.setMemberClass(v.getMemberClass());
        type.setMemberCode(v.getMemberCode());
        type.setSubsystemCode(v.getSubsystemCode());
        return type;
    }

    static XRoadServiceIdentifierType printServiceId(ServiceId v) {
        XRoadServiceIdentifierType type = new XRoadServiceIdentifierType();
        type.setObjectType(v.getObjectType());
        type.setXRoadInstance(v.getXRoadInstance());
        type.setMemberClass(v.getMemberClass());
        type.setMemberCode(v.getMemberCode());
        type.setSubsystemCode(v.getSubsystemCode());
        type.setServiceCode(v.getServiceCode());
        type.setServiceVersion(v.getServiceVersion());
        return type;
    }

    static XRoadSecurityServerIdentifierType printSecurityServerId(
            SecurityServerId v) {
        XRoadSecurityServerIdentifierType type =
                new XRoadSecurityServerIdentifierType();
        type.setObjectType(v.getObjectType());
        type.setXRoadInstance(v.getXRoadInstance());
        type.setMemberClass(v.getMemberClass());
        type.setMemberCode(v.getMemberCode());
        type.setServerCode(v.getServerCode());
        return type;
    }

    static XRoadGlobalGroupIdentifierType printGlobalGroupId(GlobalGroupId v) {
        XRoadGlobalGroupIdentifierType type =
                new XRoadGlobalGroupIdentifierType();
        type.setObjectType(v.getObjectType());
        type.setXRoadInstance(v.getXRoadInstance());
        type.setGroupCode(v.getGroupCode());
        return type;
    }

    static XRoadLocalGroupIdentifierType printLocalGroupId(LocalGroupId v) {
        XRoadLocalGroupIdentifierType type = new XRoadLocalGroupIdentifierType();
        type.setObjectType(v.getObjectType());
        type.setXRoadInstance(v.getXRoadInstance());
        type.setGroupCode(v.getGroupCode());
        return type;
    }

    static ClientId parseClientId(XRoadIdentifierType v) {
        if (XRoadObjectType.SUBSYSTEM.equals(v.getObjectType())
                && v.getSubsystemCode() == null) {
            throw new CodedException(X_INVALID_CLIENT_IDENTIFIER,
                    "Missing required subsystem code");
        }

        if (XRoadObjectType.MEMBER.equals(v.getObjectType())
                && v.getSubsystemCode() != null) {
            throw new CodedException(X_INVALID_CLIENT_IDENTIFIER,
                    "Redundant subsystem code");
        }

        return ClientId.Conf.create(v.getXRoadInstance(), v.getMemberClass(),
                v.getMemberCode(),
                XRoadObjectType.MEMBER.equals(v.getObjectType())
                        ? null : v.getSubsystemCode());
    }

    static ServiceId parseServiceId(XRoadIdentifierType v) {
        return ServiceId.Conf.create(v.getXRoadInstance(),
                v.getMemberClass(), v.getMemberCode(),
                v.getSubsystemCode(), v.getServiceCode(),
                v.getServiceVersion());
    }

    static SecurityServerId parseSecurityServerId(XRoadIdentifierType v) {
        return SecurityServerId.Conf.create(v.getXRoadInstance(),
                v.getMemberClass(), v.getMemberCode(), v.getServerCode());
    }

    static GlobalGroupId parseGlobalGroupId(XRoadIdentifierType v) {
        return GlobalGroupId.Conf.create(v.getXRoadInstance(), v.getGroupCode());
    }

    static LocalGroupId parseLocalGroupId(XRoadIdentifierType v) {
        return LocalGroupId.Conf.create(v.getGroupCode());
    }

    // -- Identifier-specific adapter classes ---------------------------------

    static class ClientIdAdapter
            extends XmlAdapter<XRoadClientIdentifierType, ClientId> {

        @Override
        public XRoadClientIdentifierType marshal(ClientId v)
                throws Exception {
            return v == null ? null : printClientId(v);
        }

        @Override
        public ClientId unmarshal(XRoadClientIdentifierType v)
                throws Exception {
            return v == null ? null : parseClientId(v);
        }
    }

    static class ServiceIdAdapter
            extends XmlAdapter<XRoadServiceIdentifierType, ServiceId> {

        @Override
        public XRoadServiceIdentifierType marshal(ServiceId v)
                throws Exception {
            return v == null ? null : printServiceId(v);
        }

        @Override
        public ServiceId unmarshal(XRoadServiceIdentifierType v)
                throws Exception {
            if (v != null) {
                switch (v.getObjectType()) {
                    case SERVICE:
                        return parseServiceId(v);
                    default:
                        return null;
                }
            } else {
                return null;
            }
        }
    }

    static class SecurityServerIdAdapter
            extends XmlAdapter<
            XRoadSecurityServerIdentifierType, SecurityServerId> {

        @Override
        public XRoadSecurityServerIdentifierType marshal(SecurityServerId v)
                throws Exception {
            return v == null ? null : printSecurityServerId(v);
        }

        @Override
        public SecurityServerId unmarshal(XRoadSecurityServerIdentifierType v)
                throws Exception {
            return v == null ? null : parseSecurityServerId(v);
        }
    }

    static class GlobalGroupIdAdapter
            extends XmlAdapter<XRoadGlobalGroupIdentifierType, GlobalGroupId> {

        @Override
        public XRoadGlobalGroupIdentifierType marshal(GlobalGroupId v)
                throws Exception {
            return v == null ? null : printGlobalGroupId(v);
        }

        @Override
        public GlobalGroupId unmarshal(XRoadGlobalGroupIdentifierType v)
                throws Exception {
            return v == null ? null : parseGlobalGroupId(v);
        }
    }

    static class LocalGroupIdAdapter
            extends XmlAdapter<XRoadLocalGroupIdentifierType, LocalGroupId> {

        @Override
        public XRoadLocalGroupIdentifierType marshal(LocalGroupId v)
                throws Exception {
            return v == null ? null : printLocalGroupId(v);
        }

        @Override
        public LocalGroupId unmarshal(XRoadLocalGroupIdentifierType v)
                throws Exception {
            return v == null ? null : parseLocalGroupId(v);
        }
    }

    static class GenericXRoadIdAdapter
            extends XmlAdapter<XRoadIdentifierType, XRoadId> {

        @Override
        public XRoadIdentifierType marshal(XRoadId v) throws Exception {
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
                case GLOBALGROUP:
                    return printGlobalGroupId((GlobalGroupId) v);
                case LOCALGROUP:
                    return printLocalGroupId((LocalGroupId) v);
                default:
                    throw new CodedException(X_INTERNAL_ERROR,
                            "Unsupported object type: " + v.getObjectType());
            }
        }

        @Override
        public XRoadId unmarshal(XRoadIdentifierType v) throws Exception {
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
                case GLOBALGROUP:
                    return parseGlobalGroupId(v);
                case LOCALGROUP:
                    return parseLocalGroupId(v);
                default:
                    throw new CodedException(X_INTERNAL_ERROR,
                            "Unsupported object type: " + v.getObjectType());
            }
        }
    }
}
