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
package org.niis.xroad.ds.issuance.identity;

import lombok.RequiredArgsConstructor;
import org.eclipse.edc.identityhub.protocols.dcp.issuer.spi.DcpIssuerService;
import org.eclipse.edc.identityhub.protocols.dcp.spi.model.CredentialRequestMessage;
import org.eclipse.edc.identityhub.protocols.dcp.spi.model.DcpRequestContext;
import org.eclipse.edc.spi.result.ServiceResult;

import static org.niis.xroad.ds.issuance.identity.XRoadIssuanceGateExtension.NAME;

/**
 * Decorator over EDC's {@link DcpIssuerService} that refuses every DCP (Decentralized Claims Protocol)
 * credential request while the issuer identity is unbound, read live from a {@link BoundIssuerIdentityHolder}.
 * Refusing to issue is a direct rule here, not a side effect of {@link XRoadStatusListCredentialPublisher}'s
 * own refusal: a credential request that needs no statuslist entry would otherwise still succeed while unbound.
 */
@RequiredArgsConstructor
public class XRoadIssuanceGate implements DcpIssuerService {

    private final DcpIssuerService delegate;
    private final BoundIssuerIdentityHolder holder;

    @Override
    public ServiceResult<CredentialRequestMessage.Response> initiateCredentialsIssuance(
            String participantContextId, CredentialRequestMessage message, DcpRequestContext context) {
        if (holder.identity().isEmpty()) {
            return ServiceResult.conflict(NAME + ": the issuer identity is not bound");
        }
        return delegate.initiateCredentialsIssuance(participantContextId, message, context);
    }
}
