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
package org.niis.xroad.edc.issuer.provisioning;

import org.eclipse.edc.identityhub.spi.participantcontext.IdentityHubParticipantContextService;
import org.eclipse.edc.issuerservice.spi.issuance.attestation.AttestationDefinitionService;
import org.eclipse.edc.issuerservice.spi.issuance.credentialdefinition.CredentialDefinitionService;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.niis.xroad.common.rpc.server.RpcResponseHandler;
import org.niis.xroad.edc.extension.rpc.GrpcServiceRegistry;

/**
 * EDC ServiceExtension that registers the issuer provisioning gRPC service onto the runtime's shared gRPC server.
 */
@Extension(value = IssuerProvisioningExtension.EXTENSION_NAME)
public class IssuerProvisioningExtension implements ServiceExtension {

    public static final String EXTENSION_NAME = "X-Road Issuer Provisioning gRPC Extension";

    @Inject
    private IdentityHubParticipantContextService participantContextService;

    @Inject
    private AttestationDefinitionService attestationDefinitionService;

    @Inject
    private CredentialDefinitionService credentialDefinitionService;

    @Inject
    private GrpcServiceRegistry grpcServiceRegistry;

    @Inject
    private Monitor monitor;

    @Override
    public String name() {
        return EXTENSION_NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var grpcService = new IssuerProvisioningGrpcService(
                participantContextService, attestationDefinitionService, credentialDefinitionService,
                new RpcResponseHandler());
        grpcServiceRegistry.register(grpcService);
        monitor.info("Initialized extension: " + EXTENSION_NAME);
    }
}
