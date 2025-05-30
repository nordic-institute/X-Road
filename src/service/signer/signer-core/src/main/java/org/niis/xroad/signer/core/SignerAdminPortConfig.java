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
package org.niis.xroad.signer.core;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.util.AdminPort;
import ee.ria.xroad.common.util.JsonUtils;
import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.ResponseWrapper;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.globalconf.status.CertificationServiceDiagnostics;
import org.niis.xroad.signer.core.certmanager.OcspClientWorker;
import org.niis.xroad.signer.core.job.OcspClientExecuteScheduler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.Optional;

import static org.eclipse.jetty.http.MimeTypes.Type.APPLICATION_JSON_UTF_8;

@Slf4j
@Configuration
public class SignerAdminPortConfig {

    @Bean
    CertificationServiceDiagnostics certificationServiceDiagnostics() {
        return new CertificationServiceDiagnostics();
    }

    @Bean
    AdminPort createAdminPort(final CertificationServiceDiagnostics diagnosticsDefault,
                              final OcspClientWorker ocspClientWorker,
                              final Optional<OcspClientExecuteScheduler> ocspClientExecuteScheduler) {
        var port = new AdminPort(SystemProperties.getSignerAdminPort());

        port.addHandler("/execute", new AdminPort.SynchronousCallback() {
            @Override
            public void handle(RequestWrapper request, ResponseWrapper response) {
                try {
                    if (ocspClientExecuteScheduler.isPresent()) {
                        ocspClientExecuteScheduler.get().execute();
                    } else {
                        ocspClientWorker.execute(null);
                    }
                } catch (Exception ex) {
                    log.error("error occurred in execute handler", ex);
                }
            }
        });

        port.addHandler("/status", new AdminPort.SynchronousCallback() {
            @Override
            public void handle(RequestWrapper request, ResponseWrapper response) {
                log.info("handler /status");
                CertificationServiceDiagnostics diagnostics = null;
                try {
                    diagnostics = ocspClientWorker.getDiagnostics();
                    if (diagnostics != null) {
                        diagnosticsDefault.update(diagnostics.getCertificationServiceStatusMap());
                    }
                } catch (Exception e) {
                    log.error("Error getting diagnostics status", e);
                }
                if (diagnostics == null) {
                    diagnostics = diagnosticsDefault;
                }
                try (var responseOut = response.getOutputStream()) {
                    response.setContentType(APPLICATION_JSON_UTF_8);
                    JsonUtils.getObjectWriter()
                            .writeValue(responseOut, diagnostics);
                } catch (IOException e) {
                    log.error("Error writing response", e);
                }
            }
        });

        return port;
    }

}
