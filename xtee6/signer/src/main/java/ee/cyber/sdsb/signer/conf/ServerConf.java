package ee.cyber.sdsb.signer.conf;

import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.conf.AbstractXmlConf;
import ee.cyber.sdsb.common.conf.serverconf.ClientType;
import ee.cyber.sdsb.common.conf.serverconf.ObjectFactory;
import ee.cyber.sdsb.common.conf.serverconf.ServerConfType;
import ee.cyber.sdsb.common.identifier.ClientId;


public class ServerConf extends AbstractXmlConf<ServerConfType> {

    ServerConf() {
        super(ObjectFactory.class, SystemProperties.getServerConfFile());
    }

    private boolean instanceHasMember(ClientId memberId) {
        for (ClientType member : confType.getClient()) {
            ClientId client = member.getIdentifier();
            if (memberId.equals(client) ||
                    client.subsystemContainsMember(memberId)) {
                return true;
            }
        }

        return false;
    }

    public static boolean hasMember(ClientId memberId) {
        return new ServerConf().instanceHasMember(memberId);
    }

}
