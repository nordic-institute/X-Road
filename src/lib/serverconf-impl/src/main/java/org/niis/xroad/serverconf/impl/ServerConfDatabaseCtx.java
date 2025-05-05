package org.niis.xroad.serverconf.impl;

import ee.ria.xroad.common.db.DatabaseCtx;

import org.hibernate.Interceptor;

import java.util.Map;

public class ServerConfDatabaseCtx extends DatabaseCtx {
    private static final String SERVER_CONF_DB_NAME = "serverconf";

    public ServerConfDatabaseCtx(Map<String, String> hibernateProperties) {
        super(SERVER_CONF_DB_NAME, hibernateProperties);
    }

    public ServerConfDatabaseCtx(Map<String, String> hibernateProperties, Interceptor interceptor) {
        super(SERVER_CONF_DB_NAME, hibernateProperties, interceptor);
    }
}
