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
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * DSP request. Carries the per-request context that {@link DspRequestProcessor} needs to resolve
 * asset access from the control plane. {@code managementSubsystem} marks a request whose service is
 * addressed to the federation's management subsystem client id. Whether that routes to the provider's
 * SYSTEM-context identity, rather than the provider member's context, additionally depends on the
 * service code being one the provider's SYSTEM catalog actually publishes — a decision the request
 * processor makes together with its builtin-service check. The consumer's own participant context is
 * always the sender's derived member context, independent of this flag.
 *
 * @param serviceId            the target service's identifier (non-null after SOAP/REST decoding)
 * @param sender               the consumer client that initiated the request; its member part
 *                             determines the participant context the consumer negotiates as
 * @param targetSecurityServer optional caller-sent security-server hint; when non-null the
 *                             resolver restricts provider candidates to this exact SS
 * @param managementSubsystem  {@code true} when the request's service is addressed to the federation's
 *                             management subsystem client id
 */
public record DspRequest(ServiceId serviceId, @Nonnull ClientId sender, @Nullable SecurityServerId targetSecurityServer,
                         boolean managementSubsystem) {
}
