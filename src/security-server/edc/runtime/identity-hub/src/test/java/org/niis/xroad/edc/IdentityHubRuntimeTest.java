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


import org.eclipse.edc.junit.annotations.EndToEndTest;
import org.eclipse.edc.junit.extensions.RuntimeExtension;
import org.eclipse.edc.junit.extensions.RuntimePerClassExtension;
import org.eclipse.edc.util.io.Ports;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.File;
import java.nio.file.Paths;
import java.util.Map;

@EndToEndTest
class IdentityHubRuntimeTest {

    static {
        System.setProperty("xroad.signer.grpc-tls-enabled", "false");

        String xrdSrcDir = Paths.get("./src/test/resources/files/").toAbsolutePath().normalize().toString();
        System.setProperty("xroad.signer.key-configuration-file", xrdSrcDir + "/signer/keyconf.xml");
    }

    @RegisterExtension
    private static final RuntimeExtension RUNTIME = new RuntimePerClassExtension()
            .setConfiguration(Map.ofEntries(
                    Map.entry("edc.vault.hashicorp.url", "http://url"),
                    Map.entry("edc.vault.hashicorp.token", "token"),
                    Map.entry("edc.participant.id", "did:web:localhost"),
                    Map.entry("web.http.resolution.path", "/resolution"),
                    Map.entry("web.http.resolution.port", String.valueOf(Ports.getFreePort())),
                    Map.entry("web.http.port", String.valueOf(Ports.getFreePort())),
                    Map.entry("web.http.path", "/api"),
                    Map.entry("web.http.identity.port", String.valueOf(Ports.getFreePort())),
                    Map.entry("web.http.identity.path", "/identity"),
                    Map.entry("edc.iam.sts.privatekey.alias", "alias_ss0"),
                    Map.entry("EDC_HOSTNAME", "ss0")
            ));

    @Test
    void verifyStartup() {
        //do nothing
    }

}
