/*
 * The MIT License
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
package ee.ria.xroad.proxy;

import ee.ria.xroad.common.PortNumbers;
import ee.ria.xroad.common.util.AdminPort;
import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.ResponseWrapper;
import ee.ria.xroad.common.util.healthcheck.HealthCheckPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.http.MimeTypes;
import org.niis.xroad.proxy.edc.AssetsRegistrationJob;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.util.Optional;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class ProxyAdminPortConfig {


    private final Optional<AssetsRegistrationJob> assetsRegistrationJobProvider;
    private final Optional<HealthCheckPort> healthCheckPort;

    @Bean(initMethod = "start", destroyMethod = "stop")
    AdminPort createAdminPort() {
        AdminPort adminPort = new AdminPort(PortNumbers.ADMIN_PORT);

        addMaintenanceHandler(adminPort);

        assetsRegistrationJobProvider.ifPresent(assetsRegistrationJob ->
                addDsAssetCreationTriggerHandler(adminPort, assetsRegistrationJob));

        return adminPort;
    }

    private void addMaintenanceHandler(AdminPort adminPort) {
        adminPort.addHandler("/maintenance", new AdminPort.SynchronousCallback() {
            @Override
            public void handle(RequestWrapper request, ResponseWrapper response) throws Exception {

                String result = "Invalid parameter 'targetState', request ignored";
                String param = request.getParameter("targetState");

                if (param != null && (param.equalsIgnoreCase("true") || param.equalsIgnoreCase("false"))) {
                    result = setHealthCheckMaintenanceMode(Boolean.parseBoolean(param));
                }
                try (var pw = new PrintWriter(response.getOutputStream())) {
                    response.setContentType(MimeTypes.Type.APPLICATION_JSON_UTF_8);
                    pw.println(result);
                }
            }
        });
    }

    private void addDsAssetCreationTriggerHandler(AdminPort adminPort, AssetsRegistrationJob assetsRegistrationJob) {
        adminPort.addHandler("/trigger-ds-asset-creation", new AdminPort.SynchronousCallback() {
            @Override
            public void handle(RequestWrapper request, ResponseWrapper response) throws Exception {
                assetsRegistrationJob.registerDataPlane();
                assetsRegistrationJob.registerAssets();

                try (var pw = new PrintWriter(response.getOutputStream())) {
                    response.setContentType(MimeTypes.Type.TEXT_PLAIN_UTF_8);
                    pw.println("OK");
                    response.setStatus(HttpURLConnection.HTTP_OK);
                }
            }
        });
    }

    private String setHealthCheckMaintenanceMode(boolean targetState) {
        return healthCheckPort.map(port -> port.setMaintenanceMode(targetState))
                .orElse("No HealthCheckPort found, maintenance mode not set");
    }

}
