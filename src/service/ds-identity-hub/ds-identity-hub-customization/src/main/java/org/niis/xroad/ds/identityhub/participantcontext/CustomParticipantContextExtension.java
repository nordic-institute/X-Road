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

package org.niis.xroad.ds.identityhub.participantcontext;

import org.eclipse.edc.identityhub.spi.did.store.DidResourceStore;
import org.eclipse.edc.identityhub.spi.participantcontext.IdentityHubParticipantContextService;
import org.eclipse.edc.identityhub.spi.participantcontext.StsAccountProvisioner;
import org.eclipse.edc.identityhub.spi.participantcontext.events.ParticipantContextObservable;
import org.eclipse.edc.participantcontext.spi.config.service.ParticipantContextConfigService;
import org.eclipse.edc.participantcontext.spi.store.ParticipantContextStore;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.transaction.spi.TransactionContext;

import static org.niis.xroad.ds.identityhub.participantcontext.CustomParticipantContextExtension.NAME;

/**
 * Overrides the default {@link IdentityHubParticipantContextService} provider from
 * {@code ParticipantContextExtension} with {@link CustomIdentityHubParticipantContextService},
 * which correctly activates participant contexts when {@code active = true} is requested.
 *
 * <p>Because this extension injects {@link ParticipantContextObservable} (which is provided
 * by {@code ParticipantContextExtension}), EDC's dependency ordering guarantees that this
 * extension's {@link #createParticipantService()} provider runs after the EDC default,
 * and therefore overwrites it in the service registry.
 */
@Extension(NAME)
public class CustomParticipantContextExtension implements ServiceExtension {

    static final String NAME = "X-Road Participant Context Extension";

    @Inject
    private ParticipantContextStore participantContextStore;

    @Inject
    private DidResourceStore didResourceStore;

    @Inject
    private Vault vault;

    @Inject
    private TransactionContext transactionContext;

    @Inject
    private ParticipantContextObservable participantContextObservable;

    @Inject
    private StsAccountProvisioner stsAccountProvisioner;

    @Inject
    private ParticipantContextConfigService configService;

    @Override
    public String name() {
        return NAME;
    }

    @Provider
    public IdentityHubParticipantContextService createParticipantService() {
        return new CustomIdentityHubParticipantContextService(
                participantContextStore, didResourceStore, vault, transactionContext,
                participantContextObservable, stsAccountProvisioner, configService);
    }
}
