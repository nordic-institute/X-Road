package org.niis.xroad.confclient;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public class ConfClientTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "xroad.common.rpc.use-tls", "false",
                "xroad.common.global-conf.source", "FILESYSTEM"
        );
    }

}
