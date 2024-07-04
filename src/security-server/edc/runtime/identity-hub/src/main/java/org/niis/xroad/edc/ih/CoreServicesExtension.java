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
package org.niis.xroad.edc.ih;

import org.eclipse.edc.iam.identitytrust.spi.verification.SignatureSuiteRegistry;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialFormat;
import org.eclipse.edc.identityhub.spi.model.IdentityHubConstants;
import org.eclipse.edc.identityhub.spi.verifiablecredentials.generator.PresentationCreatorRegistry;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.keys.spi.PrivateKeyResolver;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.verifiablecredentials.linkeddata.LdpIssuer;
import org.niis.xroad.edc.ih.vp.ExtLdpPresentationGenerator;

import static org.eclipse.edc.spi.constants.CoreConstants.JSON_LD;

@Extension("Xroad IH core customizations.")
public class CoreServicesExtension implements ServiceExtension {
    private static final String DEFAULT_SUITE = IdentityHubConstants.JWS_2020_SIGNATURE_SUITE;

    @Inject
    private JsonLd jsonLd;
    @Inject
    private PrivateKeyResolver privateKeyResolver;
    @Inject
    private PresentationCreatorRegistry presentationCreatorRegistry;
    @Inject
    private SignatureSuiteRegistry signatureSuiteRegistry;
    @Inject
    private TypeManager typeManager;

    @Override
    public void initialize(ServiceExtensionContext context) {
        var ldpIssuer = LdpIssuer.Builder.newInstance().jsonLd(jsonLd).monitor(context.getMonitor()).build();

        //This overrides default JSON_LD VP creator
        presentationCreatorRegistry.addCreator(new ExtLdpPresentationGenerator(privateKeyResolver,
                        signatureSuiteRegistry, DEFAULT_SUITE, ldpIssuer, typeManager.getMapper(JSON_LD)),
                CredentialFormat.JSON_LD);
    }

}
