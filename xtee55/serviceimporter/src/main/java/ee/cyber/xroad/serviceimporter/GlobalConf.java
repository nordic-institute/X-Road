package ee.cyber.xroad.serviceimporter;

import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.conf.AbstractXmlConf;
import ee.cyber.sdsb.common.conf.globalconf.ObjectFactory;
import ee.cyber.sdsb.common.conf.globalconf.GlobalConfType;
import ee.cyber.sdsb.common.conf.globalconf.MemberType;
import ee.cyber.sdsb.common.identifier.ClientId;

class GlobalConf extends AbstractXmlConf<GlobalConfType> {

    public GlobalConf() {
        super(ObjectFactory.class, SystemProperties.getGlobalConfFile());
    }

    public GlobalConfType getConfType() {
        return confType;
    }

    public String getMemberName(ClientId clientId) {
        for (MemberType member : confType.getMember()) {
            if (member.getMemberClass().equals(clientId.getMemberClass()) &&
                member.getMemberCode().equals(clientId.getMemberCode())) {

                return member.getName();
            }
        }

        return null;
    }

    public ClientId getManagementRequestServiceId() {
        return confType.getGlobalSettings().getManagementRequestServiceId();
    }
}
