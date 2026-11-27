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
package org.niis.xroad.proxy.core.dsp;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

import org.niis.xroad.common.core.BuiltinServiceCodes;
import org.niis.xroad.common.core.ManagementServiceCodes;

/**
 * The participant a call is addressed to: the service provider's member context, or the provider server's SYSTEM
 * context for management and monitoring services.
 *
 * @param provider the client that provides the called service
 * @param system   {@code true} when the call targets the provider server's SYSTEM context
 */
public record CalledTarget(ClientId provider, boolean system) {

    /**
     * Resolves the target of a call. A request addressed to the management subsystem for a service code that the
     * provider's SYSTEM catalog publishes, or a monitoring request, targets the SYSTEM context; a request merely
     * hosted under the management subsystem's client id (e.g. {@code listMethods}) targets the member context.
     *
     * @param serviceId           the called service
     * @param managementSubsystem {@code true} when the service is addressed to the management subsystem's client id
     */
    public static CalledTarget of(ServiceId serviceId, boolean managementSubsystem) {
        return new CalledTarget(serviceId.getClientId(), targetsSystemContext(serviceId, managementSubsystem));
    }

    private static boolean targetsSystemContext(ServiceId serviceId, boolean managementSubsystem) {
        var managementService = managementSubsystem
                && serviceId.getServiceVersion() == null
                && ManagementServiceCodes.DSP_NEGOTIATED.contains(serviceId.getServiceCode());
        var monitoringService = serviceId.getSubsystemCode() == null
                && BuiltinServiceCodes.MONITORING.contains(serviceId.getServiceCode());
        return managementService || monitoringService;
    }
}
