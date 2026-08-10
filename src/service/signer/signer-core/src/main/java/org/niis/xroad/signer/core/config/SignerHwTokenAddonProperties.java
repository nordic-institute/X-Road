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
package org.niis.xroad.signer.core.config;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.properties.config.XRoadConfig;

import java.time.Duration;

import static org.niis.xroad.signer.common.config.SignerConfigKeys.HWTOKEN_ENABLED;
import static org.niis.xroad.signer.common.config.SignerConfigKeys.HWTOKEN_POOL_ENABLED;
import static org.niis.xroad.signer.common.config.SignerConfigKeys.HWTOKEN_POOL_MAX_IDLE;
import static org.niis.xroad.signer.common.config.SignerConfigKeys.HWTOKEN_POOL_MAX_TOTAL;
import static org.niis.xroad.signer.common.config.SignerConfigKeys.HWTOKEN_POOL_MIN_IDLE;
import static org.niis.xroad.signer.common.config.SignerConfigKeys.HWTOKEN_SESSION_ACQUIRE_TIMEOUT;

/** Hardware-token addon properties ({@code xroad.signer.addon.hwtoken.*}). */
@RequiredArgsConstructor
public class SignerHwTokenAddonProperties {

    private final XRoadConfig xRoadConfig;

    /** @return whether the hardware-token addon is enabled */
    public boolean enabled() {
        return xRoadConfig.value(HWTOKEN_ENABLED);
    }

    /** @return whether the session pool is enabled */
    public boolean poolEnabled() {
        return xRoadConfig.value(HWTOKEN_POOL_ENABLED);
    }

    /** @return maximum total sessions in the pool */
    public int poolMaxTotal() {
        return xRoadConfig.value(HWTOKEN_POOL_MAX_TOTAL);
    }

    /** @return minimum number of idle sessions in the pool */
    public int poolMinIdle() {
        return xRoadConfig.value(HWTOKEN_POOL_MIN_IDLE);
    }

    /** @return maximum number of idle sessions in the pool */
    public int poolMaxIdle() {
        return xRoadConfig.value(HWTOKEN_POOL_MAX_IDLE);
    }

    /** @return timeout for acquiring a session from the pool */
    public Duration sessionAcquireTimeout() {
        return xRoadConfig.value(HWTOKEN_SESSION_ACQUIRE_TIMEOUT);
    }
}
