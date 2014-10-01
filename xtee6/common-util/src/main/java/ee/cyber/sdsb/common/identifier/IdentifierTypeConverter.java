package ee.cyber.sdsb.common.identifier;

import javax.xml.bind.annotation.adapters.XmlAdapter;

import ee.cyber.sdsb.common.CodedException;

import static ee.cyber.sdsb.common.ErrorCodes.X_INTERNAL_ERROR;

/**
 * Adapter class for converting between DTO and XML identifier types.
 */
class IdentifierTypeConverter {

    // -- Type conversion methods ---------------------------------------------

    static SdsbClientIdentifierType printClientId(ClientId v) {
        SdsbClientIdentifierType type = new SdsbClientIdentifierType();
        type.setObjectType(v.getObjectType());
        type.setSdsbInstance(v.getSdsbInstance());
        type.setMemberClass(v.getMemberClass());
        type.setMemberCode(v.getMemberCode());
        type.setSubsystemCode(v.getSubsystemCode());
        return type;
    }

    static SdsbServiceIdentifierType printServiceId(ServiceId v) {
        SdsbServiceIdentifierType type = new SdsbServiceIdentifierType();
        type.setObjectType(v.getObjectType());
        type.setSdsbInstance(v.getSdsbInstance());
        type.setMemberClass(v.getMemberClass());
        type.setMemberCode(v.getMemberCode());
        type.setSubsystemCode(v.getSubsystemCode());
        type.setServiceCode(v.getServiceCode());
        type.setServiceVersion(v.getServiceVersion());
        return type;
    }

    static SdsbSecurityCategoryIdentifierType printSecurityCategoryId(
            SecurityCategoryId v) {
        SdsbSecurityCategoryIdentifierType type =
                new SdsbSecurityCategoryIdentifierType();
        type.setObjectType(v.getObjectType());
        type.setSdsbInstance(v.getSdsbInstance());
        type.setSecurityCategoryCode(v.getCategoryCode());
        return type;
    }

    static SdsbCentralServiceIdentifierType printCentralServiceId(
            CentralServiceId v) {
        SdsbCentralServiceIdentifierType type =
                new SdsbCentralServiceIdentifierType();
        type.setObjectType(v.getObjectType());
        type.setSdsbInstance(v.getSdsbInstance());
        type.setServiceCode(v.getServiceCode());
        return type;
    }

    static SdsbSecurityServerIdentifierType printSecurityServerId(
            SecurityServerId v) {
        SdsbSecurityServerIdentifierType type =
                new SdsbSecurityServerIdentifierType();
        type.setObjectType(v.getObjectType());
        type.setSdsbInstance(v.getSdsbInstance());
        type.setMemberClass(v.getMemberClass());
        type.setMemberCode(v.getMemberCode());
        type.setServerCode(v.getServerCode());
        return type;
    }

    static SdsbGlobalGroupIdentifierType printGlobalGroupId(GlobalGroupId v) {
        SdsbGlobalGroupIdentifierType type =
                new SdsbGlobalGroupIdentifierType();
        type.setObjectType(v.getObjectType());
        type.setSdsbInstance(v.getSdsbInstance());
        type.setGroupCode(v.getGroupCode());
        return type;
    }

    static SdsbLocalGroupIdentifierType printLocalGroupId(LocalGroupId v) {
        SdsbLocalGroupIdentifierType type = new SdsbLocalGroupIdentifierType();
        type.setObjectType(v.getObjectType());
        type.setSdsbInstance(v.getSdsbInstance());
        type.setGroupCode(v.getGroupCode());
        return type;
    }

    static ClientId parseClientId(SdsbIdentifierType v) {
        return ClientId.create(v.getSdsbInstance(), v.getMemberClass(),
                v.getMemberCode(), v.getSubsystemCode());
    }

    static ServiceId parseServiceId(SdsbIdentifierType v) {
        return ServiceId.create(v.getSdsbInstance(),
                v.getMemberClass(), v.getMemberCode(),
                v.getSubsystemCode(), v.getServiceCode(),
                v.getServiceVersion());
    }

    static SecurityCategoryId parseSecurityCategoryId(SdsbIdentifierType v) {
        return SecurityCategoryId.create(v.getSdsbInstance(),
                v.getSecurityCategoryCode());
    }

    static CentralServiceId parseCentralServiceId(SdsbIdentifierType v) {
        return CentralServiceId.create(v.getSdsbInstance(),
                v.getServiceCode());
    }

    static SecurityServerId parseSecurityServerId(SdsbIdentifierType v) {
        return SecurityServerId.create(v.getSdsbInstance(),
                v.getMemberClass(), v.getMemberCode(), v.getServerCode());
    }

    static GlobalGroupId parseGlobalGroupId(SdsbIdentifierType v) {
        return GlobalGroupId.create(v.getSdsbInstance(), v.getGroupCode());
    }

    static LocalGroupId parseLocalGroupId(SdsbIdentifierType v) {
        return LocalGroupId.create(v.getGroupCode());
    }

    // -- Identifier-specific adapter classes ---------------------------------

    static class ClientIdAdapter
        extends XmlAdapter<SdsbClientIdentifierType, ClientId> {

        @Override
        public SdsbClientIdentifierType marshal(ClientId v)
                throws Exception {
            return v == null ? null : printClientId(v);
        }

        @Override
        public ClientId unmarshal(SdsbClientIdentifierType v)
                throws Exception {
            return v == null ? null : parseClientId(v);
        }
    }

    static class ServiceIdAdapter
        extends XmlAdapter<SdsbServiceIdentifierType, ServiceId> {

        @Override
        public SdsbServiceIdentifierType marshal(ServiceId v)
                throws Exception {
            return v == null ? null : printServiceId(v);
        }

        @Override
        public ServiceId unmarshal(SdsbServiceIdentifierType v)
                throws Exception {
            if (v != null) {
                switch (v.getObjectType()) {
                    case SERVICE:
                        return parseServiceId(v);
                    case CENTRALSERVICE:
                        return parseCentralServiceId(v);
                }
            }

            return null;
        }
    }

    static class SecurityCategoryIdAdapter
        extends XmlAdapter<
            SdsbSecurityCategoryIdentifierType, SecurityCategoryId> {

        @Override
        public SdsbSecurityCategoryIdentifierType marshal(SecurityCategoryId v)
                throws Exception {
            return v == null ? null : printSecurityCategoryId(v);
        }

        @Override
        public SecurityCategoryId unmarshal(
                SdsbSecurityCategoryIdentifierType v) throws Exception {
            return v == null ? null : parseSecurityCategoryId(v);
        }
    }

    static class CentralServiceIdAdapter
        extends XmlAdapter<
            SdsbCentralServiceIdentifierType, CentralServiceId> {

        @Override
        public SdsbCentralServiceIdentifierType marshal(CentralServiceId v)
                throws Exception {
            return v == null ? null : printCentralServiceId(v);
        }

        @Override
        public CentralServiceId unmarshal(SdsbCentralServiceIdentifierType v)
                throws Exception {
            return v == null ? null : parseCentralServiceId(v);
        }
    }

    static class SecurityServerIdAdapter
        extends XmlAdapter<
            SdsbSecurityServerIdentifierType, SecurityServerId> {

        @Override
        public SdsbSecurityServerIdentifierType marshal(SecurityServerId v)
                throws Exception {
            return v == null ? null : printSecurityServerId(v);
        }

        @Override
        public SecurityServerId unmarshal(SdsbSecurityServerIdentifierType v)
                throws Exception {
            return v == null ? null : parseSecurityServerId(v);
        }
    }

    static class GlobalGroupIdAdapter
        extends XmlAdapter<SdsbGlobalGroupIdentifierType, GlobalGroupId> {

        @Override
        public SdsbGlobalGroupIdentifierType marshal(GlobalGroupId v)
                throws Exception {
            return v == null ? null : printGlobalGroupId(v);
        }

        @Override
        public GlobalGroupId unmarshal(SdsbGlobalGroupIdentifierType v)
                throws Exception {
            return v == null ? null : parseGlobalGroupId(v);
        }
    }

    static class LocalGroupIdAdapter
        extends XmlAdapter<SdsbLocalGroupIdentifierType, LocalGroupId> {

        @Override
        public SdsbLocalGroupIdentifierType marshal(LocalGroupId v)
                throws Exception {
            return v == null ? null : printLocalGroupId(v);
        }

        @Override
        public LocalGroupId unmarshal(SdsbLocalGroupIdentifierType v)
                throws Exception {
            return v == null ? null : parseLocalGroupId(v);
        }
    }

    static class GenericSdsbIdAdapter
        extends XmlAdapter<SdsbIdentifierType, SdsbId> {

        @Override
        public SdsbIdentifierType marshal(SdsbId v) throws Exception {
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
        public SdsbId unmarshal(SdsbIdentifierType v) throws Exception {
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
