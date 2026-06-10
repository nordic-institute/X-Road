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
package org.niis.xroad.ss.test.api;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.test.apitest.core.config.ApiTestCoreProperties;
import org.niis.xroad.test.apitest.core.container.BaseComposeSetup;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.utility.MountableFile;

import java.io.File;

import static org.testcontainers.containers.wait.strategy.Wait.forListeningPort;

@Slf4j
@SuppressWarnings("checkstyle:magicnumber")
public class SsApiTestContainerSetup extends BaseComposeSetup {

    public static final String SIGNER = "signer";
    public static final String CONFIGURATION_CLIENT = "configuration-client";
    public static final String PROXY = "proxy";
    public static final String MONITOR = "monitor";
    public static final String AUXILIARY_SERVICE = "auxiliary-service";
    public static final String TESTCA = "testca";
    public static final String DB_SERVERCONF = "db-serverconf";
    public static final String DB_MESSAGELOG = "db-messagelog";
    public static final String OP_MONITOR = "op-monitor";
    public static final String NGINX = "nginx";
    public static final String OPENBAO = "openbao";
    public static final String UI = "ui";
    public static final String DS_CONTROL_PLANE = "ds-control-plane";
    public static final String DS_IDENTITY_HUB = "ds-identity-hub";
    public static final String DS_ISSUER_SERVICE = "ds-issuer-service";

    private static final String COMPOSE_SS_FILE = "compose.main.yaml";
    private static final String COMPOSE_API_FILE = "compose.api.yaml";
    private static final String COMPOSE_API_DS_FILE = "compose.api.ds.yaml";

    public SsApiTestContainerSetup(ApiTestCoreProperties coreProperties) {
        super(coreProperties);
    }

    @Override
    public ComposeContainer initEnv() {
        return new ComposeContainer("ss-api-",
                new File(coreProperties.resourceDir() + COMPOSE_SS_FILE),
                new File(coreProperties.resourceDir() + COMPOSE_API_FILE),
                new File(coreProperties.resourceDir() + COMPOSE_API_DS_FILE))
                .withExposedService(PROXY, Port.PROXY_HTTP, forListeningPort())
                .withExposedService(PROXY, Port.PROXY_HEALTHCHECK, forListeningPort())
                .withExposedService(SIGNER, Port.QUARKUS_HEALTH, forListeningPort())
                .withExposedService(CONFIGURATION_CLIENT, Port.QUARKUS_HEALTH, forListeningPort())
                .withExposedService(OP_MONITOR, Port.QUARKUS_HEALTH, forListeningPort())
                .withExposedService(AUXILIARY_SERVICE, Port.QUARKUS_HEALTH, forListeningPort())
                .withExposedService(UI, Port.UI, forListeningPort())
                .withExposedService(DB_SERVERCONF, Port.DB, forListeningPort())
                .withExposedService(DB_MESSAGELOG, Port.DB, forListeningPort())
                .withExposedService(TESTCA, Port.TEST_CA, forListeningPort())
                .withExposedService(DS_CONTROL_PLANE, Port.DS_CONTROL_PLANE_MANAGEMENT, forListeningPort())
                .withExposedService(DS_IDENTITY_HUB, Port.DS_IDENTITY_HUB_IDENTITY, forListeningPort())
                .withExposedService(DS_ISSUER_SERVICE, Port.DS_ISSUER_SERVICE_ADMIN, forListeningPort())
                .withExposedService(DS_ISSUER_SERVICE, Port.DS_ISSUER_SERVICE_IDENTITY, forListeningPort())
                .withExposedService(DS_ISSUER_SERVICE, Port.DS_ISSUER_SERVICE_IDENTITY_DID, forListeningPort())
                .withLogConsumer(UI, createLogConsumer(UI))
                .withLogConsumer(PROXY, createLogConsumer(PROXY))
                .withLogConsumer(SIGNER, createLogConsumer(SIGNER))
                .withLogConsumer(CONFIGURATION_CLIENT, createLogConsumer(CONFIGURATION_CLIENT))
                .withLogConsumer(MONITOR, createLogConsumer(MONITOR))
                .withLogConsumer(AUXILIARY_SERVICE, createLogConsumer(AUXILIARY_SERVICE))
                .withLogConsumer(OP_MONITOR, createLogConsumer(OP_MONITOR))
                .withLogConsumer(OPENBAO, createLogConsumer(OPENBAO))
                .withLogConsumer(NGINX, createLogConsumer(NGINX))
                .withLogConsumer(TESTCA, createLogConsumer(TESTCA))
                .withLogConsumer(DS_CONTROL_PLANE, createLogConsumer(DS_CONTROL_PLANE))
                .withLogConsumer(DS_IDENTITY_HUB, createLogConsumer(DS_IDENTITY_HUB))
                .withLogConsumer(DS_ISSUER_SERVICE, createLogConsumer(DS_ISSUER_SERVICE));
    }

    @Override
    protected void onPostStart() {
        var nginxFiles = MountableFile.forClasspathResource("nginx-container-files/var/lib");
        env.getContainerByServiceName(NGINX).orElseThrow()
                .copyFileToContainer(nginxFiles, "/var/lib");
        new DspBootstrap(this).bootstrap();
    }

}
