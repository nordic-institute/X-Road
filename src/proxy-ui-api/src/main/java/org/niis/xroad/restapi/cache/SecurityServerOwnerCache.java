/**
 * The MIT License
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
package org.niis.xroad.restapi.cache;

import ee.ria.xroad.common.identifier.ClientId;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.service.ServerConfService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Request scoped (dummy POC) cache for security server owner information
 */
@RequestScope
@Component
@Slf4j
public class SecurityServerOwnerCache {

    private final ServerConfService serverConfService;

    private String securityServerOwner;

    // for testing concurrent requests
    public static final int ARTIFICIAL_DELAY = 2000;

    // just some dummy debugging fields
    private static final String LOG_MESSAGE_PREFIX = "!!!!!!!!!!!!! ";
    private static final AtomicInteger INSTANCE_COUNTER = new AtomicInteger(0);
    private final int thisInstance;

    @Autowired
    public SecurityServerOwnerCache(ServerConfService serverConfService) {
        this.serverConfService = serverConfService;
        thisInstance = INSTANCE_COUNTER.incrementAndGet();
        // constructing this instance with a @Bean builder method might be kind of cleaner design
        securityServerOwner = buildSecurityServerOwner();
        log.info(LOG_MESSAGE_PREFIX + "created SecurityServerOwnerCache instance {} - owner is {}",
                thisInstance, securityServerOwner);
    }

    public String getSecurityServerOwner() {
        maybeSlow();
        return securityServerOwner;
    }

    private String buildSecurityServerOwner() {
        log.info(LOG_MESSAGE_PREFIX + "getting security server owner " + thisInstance);
        maybeSlow();
        ClientId id = serverConfService.getSecurityServerOwnerId();
        return id.toShortString() + "(cache bean instance: " + thisInstance + ", t="
                + System.currentTimeMillis() + ")";
    }

    private void maybeSlow() {
        if (ARTIFICIAL_DELAY > 0) {
            log.info(LOG_MESSAGE_PREFIX + "being slow " + thisInstance + " on purpose ....");
            try {
                Thread.sleep(ARTIFICIAL_DELAY);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            log.info(LOG_MESSAGE_PREFIX + "...finished " + thisInstance);
        }
    }
}
