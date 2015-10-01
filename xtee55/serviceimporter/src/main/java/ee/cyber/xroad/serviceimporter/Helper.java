package ee.cyber.xroad.serviceimporter;

import ee.ria.xroad.common.conf.serverconf.dao.IdentifierDAOImpl;
import ee.ria.xroad.common.conf.serverconf.dao.ServerConfDAOImpl;
import ee.ria.xroad.common.conf.serverconf.model.ServerConfType;
import ee.ria.xroad.common.identifier.XroadId;

final class Helper {

    private Helper() {
    }

    static ServerConfType getConf() throws Exception {
        return new ServerConfDAOImpl().getConf();
    }

    static <T extends XroadId> T getIdentifier(T example) throws Exception {
        T xroadId = IdentifierDAOImpl.getIdentifier(example);
        return xroadId != null ? xroadId : example;
    }
}
