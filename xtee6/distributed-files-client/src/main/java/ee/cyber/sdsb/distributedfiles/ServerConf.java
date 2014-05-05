package ee.cyber.sdsb.distributedfiles;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.conf.AbstractXmlConf;
import ee.cyber.sdsb.common.conf.serverconf.GlobalConfDistributorType;
import ee.cyber.sdsb.common.conf.serverconf.ObjectFactory;
import ee.cyber.sdsb.common.conf.serverconf.ServerConfType;


class ServerConf extends AbstractXmlConf<ServerConfType> {

    private static final Logger LOG = LoggerFactory.getLogger(ServerConf.class);

    public ServerConf() {
        super(ObjectFactory.class, SystemProperties.getServerConfFile());
    }

    /**
     * Returns file distributors.
     */
    public List<GlobalConfDistributorType> getFileDistributors() {
        LOG.trace("getFileDistributors");

        return confType.getGlobalConfDistributor();
    }
}
