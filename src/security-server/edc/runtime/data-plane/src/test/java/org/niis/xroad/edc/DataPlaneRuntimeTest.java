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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.nio.file.Paths;
import java.util.Map;

@EndToEndTest
@Disabled
class DataPlaneRuntimeTest {

    static int controlPort;

    static {
        System.setProperty("xroad.signer.grpc-tls-enabled", "false");
        String xrdSrcDir = Paths.get("./src/test/resources/files/").toAbsolutePath().normalize().toString();
        System.setProperty("xroad.conf.path", xrdSrcDir);
        System.setProperty("xroad.signer.key-configuration-file", xrdSrcDir + "/signer/keyconf.xml");

        controlPort = Ports.getFreePort();
    }

    @RegisterExtension
    private static final RuntimeExtension RUNTIME = new RuntimePerClassExtension()
            .setConfiguration(Map.ofEntries(
                    Map.entry("web.http.port", String.valueOf(Ports.getFreePort())),
                    Map.entry("web.http.path", "/api"),
                    Map.entry("web.http.xroad.public.port", String.valueOf(Ports.getFreePort())),
                    Map.entry("web.http.xroad.public.path", "/xroad/public"),
                    Map.entry("web.http.control.port", String.valueOf(controlPort)),
                    Map.entry("web.http.control.path", "/control"),
                    Map.entry("edc.dataplane.token.validation.endpoint", "http://localhost:9192/control/token"),
                    Map.entry("edc.transfer.proxy.token.verifier.publickey.alias", "public-key"),
                    Map.entry("edc.transfer.proxy.token.signer.privatekey.alias", "private-key"),
                    Map.entry("edc.dpf.selector.url", "http://localhost:" + controlPort + "/control/v1/dataplanes"),
                    Map.entry("edc.vault.hashicorp.url", "http://url"),
                    Map.entry("edc.vault.hashicorp.token", "token")
            ));

    @Test
    void verifyStartup() {
        //do nothing
    }

}
