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

import org.eclipse.edc.identityhub.protocols.dcp.issuer.spi.DcpIssuerService;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import static org.niis.xroad.ds.issuance.identity.XRoadIssuanceGateExtension.NAME;

/**
 * Wraps the registered {@link DcpIssuerService} with {@link XRoadIssuanceGate}, so every DCP credential request
 * is refused while the issuer identity is unbound.
 *
 * <p>Ordering: declaring {@code @Provides(DcpIssuerService.class)} adds this extension to the dependency graph
 * as a provider, so {@code IssuerApiExtension} (which {@code @Inject}s {@link DcpIssuerService}) is
 * topologically ordered after us. The {@code @Inject} field resolves to the upstream {@code
 * DcpIssuerServiceImpl} (the self-edge is filtered by the dependency graph), which we then wrap and {@code
 * registerService} during {@code initialize()}.
 */
@Extension(NAME)
@Provides(DcpIssuerService.class)
public class XRoadIssuanceGateExtension implements ServiceExtension {

    static final String NAME = "X-Road Issuance Gate Extension";

    @Inject
    private DcpIssuerService delegate;

    @Inject
    private BoundIssuerIdentityHolder holder;

    public XRoadIssuanceGateExtension() {
    }

    XRoadIssuanceGateExtension(DcpIssuerService delegate, BoundIssuerIdentityHolder holder) {
        this.delegate = delegate;
        this.holder = holder;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        context.registerService(DcpIssuerService.class, new XRoadIssuanceGate(delegate, holder));
    }
}
