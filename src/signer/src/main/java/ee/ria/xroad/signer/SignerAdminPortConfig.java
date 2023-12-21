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
package ee.ria.xroad.signer;

import ee.ria.xroad.common.CertificationServiceDiagnostics;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.util.AdminPort;
import ee.ria.xroad.common.util.JsonUtils;
import ee.ria.xroad.signer.certmanager.OcspClientWorker;
import ee.ria.xroad.signer.job.OcspClientExecuteScheduler;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.Optional;

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
        AdminPort port = new SpringManagerAdminPort(SystemProperties.getSignerAdminPort());

        port.addHandler("/execute", new AdminPort.SynchronousCallback() {
            @Override
            public void handle(HttpServletRequest request, HttpServletResponse response) {
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
            public void handle(HttpServletRequest request, HttpServletResponse response) {
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
                try {
                    response.setCharacterEncoding("UTF8");
                    JsonUtils.getObjectWriter()
                            .writeValue(response.getWriter(), diagnostics);
                } catch (IOException e) {
                    log.error("Error writing response", e);
                }
            }
        });

        return port;
    }

    public static class SpringManagerAdminPort extends AdminPort {

        /**
         * Constructs an AdminPort instance that listens for commands on the given port number.
         *
         * @param portNumber the port number AdminPort will listen on
         */
        public SpringManagerAdminPort(int portNumber) {
            super(portNumber);
        }

        @PostConstruct
        public void init() throws Exception {
            start();
        }

        @PreDestroy
        public void destroy() {
            log.info("Signer shutting down...");

            try {
                stop();
                join();

            } catch (Exception e) {
                log.error("Error stopping admin port", e);
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

}
