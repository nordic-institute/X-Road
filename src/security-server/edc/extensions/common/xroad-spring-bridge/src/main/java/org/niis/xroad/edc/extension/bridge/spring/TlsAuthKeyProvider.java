package org.niis.xroad.edc.extension.bridge.spring;

import ee.ria.xroad.common.conf.globalconf.AuthKey;

public interface TlsAuthKeyProvider {
    AuthKey getAuthKey();
}
