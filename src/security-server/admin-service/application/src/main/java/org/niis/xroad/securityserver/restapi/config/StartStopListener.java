/**
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
package org.niis.xroad.securityserver.restapi.config;

import ee.ria.xroad.commonui.UIServices;
import ee.ria.xroad.signer.protocol.SignerClient;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;

/**
 * Listener which can be used to bootstrap Akka.
 * See ProxyUIServices in old proxy-ui.
 * System properties bootstrapping is done with SystemPropertiesInitializer
 */
@Slf4j
@Component
public class StartStopListener implements ApplicationListener<ApplicationEvent> {

    private UIServices uiApiActorSystem;

    private synchronized void stop() throws Exception {
        log.info("stop");

        if (uiApiActorSystem != null) {
            uiApiActorSystem.stop();
            uiApiActorSystem = null;
        }
    }

    @Autowired
    @Qualifier("signer-ip")
    private String signerIp;

    /**
     * Maybe be called multiple times since ContextRefreshedEvent can happen multiple times
     * @throws Exception
     */
    private synchronized void start() {
        log.info("start");
        if (uiApiActorSystem == null) {
            uiApiActorSystem = new UIServices("ProxyUIApi", "proxyuiapi");
            SignerClient.init(uiApiActorSystem.getActorSystem(), signerIp);
        }
    }


    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        try {
            if (event instanceof ContextClosedEvent) {
                stop();
            } else if (event instanceof ApplicationReadyEvent) {
                if (signerIp != null) {
                    // ApplicationReadyEvent happens twice, first has not injected
                    // beans such as signerIp (should always have value), second has
                    // only start the second time
                    start();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
