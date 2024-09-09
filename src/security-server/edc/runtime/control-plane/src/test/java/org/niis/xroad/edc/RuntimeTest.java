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

import java.util.Map;

@ExtendWith(EdcExtension.class)
class RuntimeTest {

    @BeforeEach
    void setUp(EdcExtension extension) {
        System.setProperty("xroad.common.grpc-internal-tls-enabled", "false");

        extension.setConfiguration(Map.ofEntries(
                Map.entry("web.http.port", String.valueOf(Ports.getFreePort())),
                Map.entry("web.http.path", "/api"),
                Map.entry("web.http.management.port", String.valueOf(Ports.getFreePort())),
                Map.entry("web.http.management.path", "/management"),
                Map.entry("web.http.control.port", String.valueOf(Ports.getFreePort())),
                Map.entry("web.http.control.path", "/control"),
                Map.entry("edc.vault.hashicorp.url", "http://url"),
                Map.entry("edc.vault.hashicorp.token", "token"),
                Map.entry("edc.iam.issuer.id", "did:web:localhost"),
                Map.entry("edc.participant.id", "did:web:localhost"),
                Map.entry("edc.iam.trusted-issuer.localhost.id", "did:web:localhost")
        ));
    }

    @Test
    void verifyStartup() {
        //do nothing
    }

}
