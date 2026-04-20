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
package org.niis.xroad.securityserver.restapi.openapi;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

import static org.niis.xroad.securityserver.restapi.openapi.BackupsApiController.DELAY_FOR_RESPONSE;

/**
 * Handles scheduling application restart after backup restore.
 * Extracted to allow mocking in tests (avoiding System.exit in the test JVM).
 */
@Slf4j
@Component
class ApplicationRestarter {

    // In Kubernetes, the auxiliary-service orchestrates all service restarts via kubectl.
    void scheduleRestartIfNeeded() {
        if (System.getenv("KUBERNETES_SERVICE_HOST") != null) {
            log.info("Kubernetes environment detected — proxy-ui-api restart is handled by auxiliary-service");
            return;
        }
        log.info("Scheduling Proxy UI restart after backup restore");
        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(Duration.ofSeconds(DELAY_FOR_RESPONSE));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            exitApplication();
        });
    }

    void exitApplication() {
        log.info("Shutting down Proxy UI for restart after backup restore");
        System.exit(1);
    }
}
