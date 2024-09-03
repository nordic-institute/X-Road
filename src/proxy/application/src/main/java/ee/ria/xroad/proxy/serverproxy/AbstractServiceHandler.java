package ee.ria.xroad.proxy.serverproxy;

import ee.ria.xroad.common.conf.serverconf.ServerConfProvider;

public abstract class AbstractServiceHandler implements ServiceHandler {
    protected final ServerConfProvider serverConfProvider;

    protected AbstractServiceHandler(ServerConfProvider serverConfProvider) {
        this.serverConfProvider = serverConfProvider;
    }
}
