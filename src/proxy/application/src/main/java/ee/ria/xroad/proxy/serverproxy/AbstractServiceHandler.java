package ee.ria.xroad.proxy.serverproxy;

import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;
import ee.ria.xroad.common.conf.serverconf.ServerConfProvider;

public abstract class AbstractServiceHandler implements ServiceHandler {
    protected final ServerConfProvider serverConfProvider;
    protected final GlobalConfProvider globalConfProvider;

    protected AbstractServiceHandler(ServerConfProvider serverConfProvider, GlobalConfProvider globalConfProvider) {
        this.serverConfProvider = serverConfProvider;
        this.globalConfProvider = globalConfProvider;
    }

}
