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
package org.niis.xroad.edc.credential;

import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;

import org.eclipse.edc.boot.BootServicesExtension;
import org.eclipse.edc.iam.did.spi.resolution.DidPublicKeyResolver;
import org.eclipse.edc.jwt.signer.spi.JwsSignerProvider;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.web.spi.WebService;

@Extension(XRoadCredentialIssuanceExtension.NAME)
public class XRoadCredentialIssuanceExtension implements ServiceExtension {

    static final String NAME = "X-Road Credential Issuance extension";

    private static final String EDC_DID_KEY_ID = "edc.did.key.id";

    @Inject
    private WebService webService;
    @Inject
    private DidPublicKeyResolver didPublicKeyResolver;
    @Inject
    private JwsSignerProvider jwsSignerProvider;
    @Inject
    private GlobalConfProvider globalConfProvider;

    @Override
    public void initialize(ServiceExtensionContext context) {
        String participantId = context.getConfig().getString(BootServicesExtension.PARTICIPANT_ID);
        var keyId = context.getConfig().getString(EDC_DID_KEY_ID);

        webService.registerResource(new XRoadMemberShipCredentialIssuanceController(
                didPublicKeyResolver, globalConfProvider, participantId, jwsSignerProvider, keyId));
    }

}
