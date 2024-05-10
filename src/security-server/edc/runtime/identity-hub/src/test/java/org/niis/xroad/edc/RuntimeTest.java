/*
 *  Copyright (c) 2022 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - initial API and implementation
 *
 */
package org.niis.xroad.edc;


import org.eclipse.edc.junit.extensions.EdcExtension;
import org.eclipse.edc.util.io.Ports;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.File;
import java.io.IOException;
import java.util.Map;

@ExtendWith(EdcExtension.class)
class RuntimeTest {

    @BeforeEach
    void setUp(EdcExtension extension) throws IOException {
        System.setProperty("xroad.common.grpc-internal-tls-enabled", "false");

        var resourcesDir = new File("src/test/resources").getAbsolutePath();
        extension.setConfiguration(Map.ofEntries(
                Map.entry("edc.vault", "%s/vault.properties".formatted(resourcesDir)),
                Map.entry("edc.keystore", "%s/cert.pfx".formatted(resourcesDir)),
                Map.entry("edc.keystore.password", "123456"),

                Map.entry("edc.iam.issuer.id", "did:web:localhost"),
                Map.entry("edc.participant.id", "did:web:localhost"),
                Map.entry("edc.iam.trusted-issuer.localhost.id", "did:web:localhost"),
                Map.entry("edc.ih.iam.id", "did:web:localhost"),
                Map.entry("edc.ih.iam.publickey.alias", "did:web:localhost"),
                Map.entry("web.http.resolution.path", "/resolution"),
                Map.entry("web.http.resolution.port", String.valueOf(Ports.getFreePort())),
                Map.entry("web.http.port", String.valueOf(Ports.getFreePort())),
                Map.entry("web.http.path", "/api"),
                Map.entry("edc.iam.sts.privatekey.alias", "alias_ss0"),
                Map.entry("edc.ih.credentials.path", "%s/credentials/".formatted(resourcesDir)),
                Map.entry("EDC_HOSTNAME", "ss0")
        ));
    }

    @Test
    void verifyStartup() {
        //do nothing
    }

}
