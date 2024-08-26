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
package org.niis.xroad.confclient.config;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.globalconf.ConfigurationClient;
import ee.ria.xroad.common.util.AdminPort;
import ee.ria.xroad.common.util.JsonUtils;
import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.ResponseWrapper;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.http.MimeTypes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.PrintWriter;

import static ee.ria.xroad.common.ErrorCodes.translateException;

@Slf4j
@Configuration
public class ConfClientAdminPortConfig {

    @Bean(initMethod = "start", destroyMethod = "stop")
    AdminPort createAdminPort(ConfigurationClient client, ConfClientJobConfig.ConfigurationClientJobListener listener) {
        var adminPort = new AdminPort(SystemProperties.getConfigurationClientAdminPort());

        adminPort.addHandler("/execute", new AdminPort.SynchronousCallback() {
            @Override
            public void handle(RequestWrapper request, ResponseWrapper response) {
                log.info("handler /execute");

                try {
                    client.execute();
                } catch (Exception e) {
                    throw translateException(e);
                }
            }
        });

        adminPort.addHandler("/status", new AdminPort.SynchronousCallback() {
            @Override
            public void handle(RequestWrapper request, ResponseWrapper response) {
                try (var writer = new PrintWriter(response.getOutputStream())) {
                    log.info("handler /status");

                    response.setContentType(MimeTypes.Type.APPLICATION_JSON_UTF_8);
                    JsonUtils.getObjectWriter().writeValue(writer, listener.getStatus());
                } catch (Exception e) {
                    log.error("Error getting conf client status", e);
                }
            }
        });

        return adminPort;
    }
}
