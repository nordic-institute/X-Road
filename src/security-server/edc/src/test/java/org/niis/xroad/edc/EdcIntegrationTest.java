/*
 * The MIT License
 *
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.edc;

import ee.ria.xroad.common.TestPortUtils;

import org.eclipse.edc.junit.extensions.EdcExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.File;
import java.io.IOException;
import java.util.Map;

@ExtendWith(EdcExtension.class)
class EdcIntegrationTest {

    @BeforeEach
    void setUp(EdcExtension extension) throws IOException {
        System.setProperty("xroad.common.grpc-internal-tls-enabled", "false");

        var resourcesDir = new File("src/main/resources").getAbsolutePath();
        extension.setConfiguration(Map.of(
                "fs.config", "%s/configuration/provider-configuration.properties".formatted(resourcesDir),
                "edc.vault", "%s/configuration/provider-vault.properties".formatted(resourcesDir),
                "edc.keystore", "%s/certs/cert.pfx".formatted(resourcesDir),
                "edc.keystore.password", "123456",
                "edc.receiver.http.endpoint", "http://localhost:4000/asset-authorization-callback",
                "edc.dataplane.token.validation.endpoint", "http://localhost:9192/control/token",
                //edc somehow fails if no property is found
                "web.http.xroad.public.port", TestPortUtils.findRandomPort().toString()
        ));
    }

    @Test
    void shouldStartup() {

    }

}
