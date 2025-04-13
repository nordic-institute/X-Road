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
package org.niis.xroad.proxy.core.configuration;

import ee.ria.xroad.common.util.AdminPort;
import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.ResponseWrapper;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.http.MimeTypes;
import org.niis.xroad.proxy.core.ProxyProperties;
import org.niis.xroad.proxy.core.healthcheck.HealthCheckPort;

import java.io.PrintWriter;

@Slf4j
public class ProxyAdminPortConfig {

    @ApplicationScoped
    @Startup
    AdminPort createAdminPort(HealthCheckPort healthCheckPort, ProxyProperties proxyProperties) throws Exception {
        AdminPort adminPort = new AdminPort(proxyProperties.adminPort());

        addMaintenanceHandler(adminPort, healthCheckPort);

        adminPort.init();

        return adminPort;
    }

    public void dispose(@Disposes AdminPort adminPort) {
        try {
            adminPort.destroy();
        } catch (Exception e) {
            log.error("Error while stopping admin port", e);
        }
    }

    private void addMaintenanceHandler(AdminPort adminPort, HealthCheckPort healthCheckPort) {
        adminPort.addHandler("/maintenance", new AdminPort.SynchronousCallback() {
            @Override
            public void handle(RequestWrapper request, ResponseWrapper response) throws Exception {

                String result = "Invalid parameter 'targetState', request ignored";
                String param = request.getParameter("targetState");

                if (param != null && (param.equalsIgnoreCase("true") || param.equalsIgnoreCase("false"))) {
                    result = setHealthCheckMaintenanceMode(Boolean.parseBoolean(param), healthCheckPort);
                }
                try (var pw = new PrintWriter(response.getOutputStream())) {
                    response.setContentType(MimeTypes.Type.APPLICATION_JSON_UTF_8);
                    pw.println(result);
                }
            }
        });
    }

    private String setHealthCheckMaintenanceMode(boolean targetState, HealthCheckPort healthCheckPort) {
        if (healthCheckPort.isEnabled()) {
            return healthCheckPort.setMaintenanceMode(targetState);
        } else {
            return "No HealthCheckPort found, maintenance mode not set";
        }
    }

}
