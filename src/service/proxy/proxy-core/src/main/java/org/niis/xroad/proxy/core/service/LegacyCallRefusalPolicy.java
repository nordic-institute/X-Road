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
package org.niis.xroad.proxy.core.service;

import ee.ria.xroad.common.identifier.ServiceId;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.ErrorOrigin;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;

import static org.niis.xroad.common.core.exception.ErrorCode.LEGACY_CALL_REFUSED;

/**
 * Answers whether this Security Server refuses calls that carry no agreement token. Refusal is in force when the
 * local setting is on or the relevant instance publishes {@code legacyProtocolDisabled}; a refusing value always
 * wins. The instance value is read from the global configuration on every call, so a refresh takes effect
 * without a restart.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class LegacyCallRefusalPolicy {

    private final ProxyProperties proxyProperties;
    private final GlobalConfProvider globalConfProvider;

    /**
     * @return true when this server, acting as service provider, refuses legacy calls
     */
    public boolean refusedAsProvider() {
        return proxyProperties.legacyProtocolDisabled()
                || globalConfProvider.isLegacyProtocolDisabled(globalConfProvider.getInstanceIdentifier());
    }

    /**
     * @param providerInstance the instance identifier of the service provider being called
     * @return true when this server, acting as service consumer, refuses legacy calls to that provider
     */
    public boolean refusedAsConsumer(String providerInstance) {
        return proxyProperties.legacyProtocolDisabled()
                || globalConfProvider.isLegacyProtocolDisabled(providerInstance);
    }

    /**
     * Fails an unnegotiated call this server, acting as service consumer, must not make. An unexpected error while
     * checking the settings refuses the call as well.
     *
     * @param service the service being called
     * @throws XrdRuntimeException with code {@code LEGACY_CALL_REFUSED} when the call is refused
     */
    public void checkConsumerCallAllowed(ServiceId service) {
        boolean refused;
        try {
            refused = refusedAsConsumer(service.getXRoadInstance());
        } catch (RuntimeException e) {
            log.warn("could not read whether legacy calls are refused; refusing the call: service={}", service, e);
            refused = true;
        }
        if (refused) {
            throw XrdRuntimeException.systemException(LEGACY_CALL_REFUSED)
                    .details(("Call to %s is refused because it is not negotiated and legacy calls are refused by "
                            + "the Security Server setting or by the provider instance setting").formatted(service))
                    .origin(ErrorOrigin.CLIENT)
                    .build();
        }
    }
}
