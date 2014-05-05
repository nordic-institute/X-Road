package ee.cyber.xroad.serviceimporter;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import ee.cyber.sdsb.common.conf.serverconf.dao.IdentifierDAOImpl;
import ee.cyber.sdsb.common.conf.serverconf.dao.ServerConfDAOImpl;
import ee.cyber.sdsb.common.conf.serverconf.model.ServerConfType;
import ee.cyber.sdsb.common.identifier.SdsbId;

class Helper {

    static boolean confExists() throws Exception {
        return ServerConfDAOImpl.getInstance().confExists();
    }

    static ServerConfType getConf() throws Exception {
        return ServerConfDAOImpl.getInstance().getConf();
    }

    static <T extends SdsbId> T getIdentifier(T example) throws Exception {
        T sdsbId = IdentifierDAOImpl.getIdentifier(example);
        return sdsbId != null ? sdsbId : example;
    }

    static String urlEncode(String value) throws UnsupportedEncodingException {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }

}
