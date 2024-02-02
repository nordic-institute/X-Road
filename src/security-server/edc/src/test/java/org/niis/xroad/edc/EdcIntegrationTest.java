package org.niis.xroad.edc;

import org.eclipse.edc.junit.extensions.EdcExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.File;
import java.util.Map;

@ExtendWith(EdcExtension.class)
class EdcIntegrationTest {

    @BeforeEach
    void setUp(EdcExtension extension) {
        var resourcesDir = new File("src/main/resources").getAbsolutePath();
        extension.setConfiguration(Map.of(
                "fs.config", "%s/configuration/provider-configuration.properties".formatted(resourcesDir),
                "edc.vault", "%s/configuration/provider-vault.properties".formatted(resourcesDir),
                "edc.keystore", "%s/certs/cert.pfx".formatted(resourcesDir),
                "edc.keystore.password", "123456",
                "edc.receiver.http.endpoint", "http://localhost:4000/asset-authorization-callback",
                "edc.dataplane.token.validation.endpoint", "http://localhost:9192/control/token"
        ));
    }

    @Test
    void shouldStartup() {

    }
}
