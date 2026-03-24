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

package org.niis.xroad.ds.identityhub.application;

import org.eclipse.edc.iam.did.spi.document.Service;
import org.eclipse.edc.identityhub.spi.participantcontext.IdentityHubParticipantContextService;
import org.eclipse.edc.identityhub.spi.participantcontext.model.KeyDescriptor;
import org.eclipse.edc.identityhub.spi.participantcontext.model.ParticipantManifest;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Extension(IdentityHubParticipantContextInitExtension.NAME)
public class IdentityHubParticipantContextInitExtension implements ServiceExtension {

    public static final String NAME = "Identity Hub Participant Context Init Extension";

    @Inject
    private IdentityHubParticipantContextService participantContextService;

    private ServiceExtensionContext context;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        this.context = context;
    }

    @Override
    public void start() {
        var publicHostname = context.getSetting("edc.ih.did.public.hostname",
                context.getSetting("edc.hostname", "localhost"));
        var participantId = "did:web:" + publicHostname;

        var existing = participantContextService.getParticipantContext(participantId);
        if (existing.succeeded()) {
            context.getMonitor().info("Participant context already exists for '%s', skipping creation.".formatted(participantId));
            return;
        }

        var credentialServiceUrl = "https://" + publicHostname + "/api/credentials/v1/participants/"
                + URLEncoder.encode(participantId, StandardCharsets.UTF_8);

        var manifest = ParticipantManifest.Builder.newInstance()
                .participantContextId(participantId)
                .did(participantId)
                .active(true)
                .serviceEndpoint(new Service(
                        participantId + "-credential-service",
                        "CredentialService",
                        credentialServiceUrl))
                .key(KeyDescriptor.Builder.newInstance()
                        .keyId(participantId + "#key-1")
                        .privateKeyAlias("key-" + participantId)
                        .keyGeneratorParams(Map.of("algorithm", "EC", "curve", "P-256"))
                        .active(true)
                        .build())
                .build();

        var result = participantContextService.createParticipantContext(manifest);
        if (result.failed()) {
            context.getMonitor().warning("Failed to create participant context for '%s': %s"
                    .formatted(participantId, result.getFailureDetail()));
        } else {
            context.getMonitor().info("Participant context created successfully for '%s'.".formatted(participantId));
        }
    }
}
