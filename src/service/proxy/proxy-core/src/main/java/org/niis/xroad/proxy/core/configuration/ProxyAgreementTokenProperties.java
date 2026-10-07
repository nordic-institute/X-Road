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

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.agreementtoken.AgreementTokenProtocolProperties;
import org.niis.xroad.common.properties.config.XRoadConfig;

import java.time.Duration;

import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.AGREEMENT_TOKEN_AUDIENCE;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.AGREEMENT_TOKEN_EXPIRY_LEEWAY;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.AGREEMENT_TOKEN_GRANT_LOOKUP_DEADLINE;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.AGREEMENT_TOKEN_ISSUER;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.AGREEMENT_TOKEN_KEY_REFRESH_INTERVAL;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.AGREEMENT_TOKEN_TOKEN_TTL;

/**
 * Proxy agreement-token configuration ({@code xroad.proxy.agreement-token.*}). Both the minting data plane and
 * the verifying server proxy read {@link #issuer()}, {@link #audience()}, {@link #tokenTtl()} and
 * {@link #expiryLeeway()} from the same bean; {@link #keyRefreshInterval()} additionally bounds how often the
 * key material bean reloads its snapshot from the secret store, and {@link #grantLookupDeadline()} bounds the
 * control-plane grant lookup on the data-flow start path.
 */
@RequiredArgsConstructor
public class ProxyAgreementTokenProperties implements AgreementTokenProtocolProperties {

    private final XRoadConfig xRoadConfig;

    @Override
    public String issuer() {
        return xRoadConfig.value(AGREEMENT_TOKEN_ISSUER);
    }

    @Override
    public String audience() {
        return xRoadConfig.value(AGREEMENT_TOKEN_AUDIENCE);
    }

    @Override
    public Duration tokenTtl() {
        return xRoadConfig.value(AGREEMENT_TOKEN_TOKEN_TTL);
    }

    @Override
    public Duration expiryLeeway() {
        return xRoadConfig.value(AGREEMENT_TOKEN_EXPIRY_LEEWAY);
    }

    /** @return how often the key-material bean retries construction or refreshes its cached snapshot */
    public Duration keyRefreshInterval() {
        return xRoadConfig.value(AGREEMENT_TOKEN_KEY_REFRESH_INTERVAL);
    }

    /** @return the deadline for the control-plane grant lookup on the data-flow start path */
    public Duration grantLookupDeadline() {
        return xRoadConfig.value(AGREEMENT_TOKEN_GRANT_LOOKUP_DEADLINE);
    }
}
