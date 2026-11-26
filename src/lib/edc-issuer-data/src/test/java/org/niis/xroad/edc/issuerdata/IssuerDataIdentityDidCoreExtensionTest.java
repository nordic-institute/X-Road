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

package org.niis.xroad.edc.issuerdata;

import org.eclipse.edc.boot.system.injection.ObjectFactory;
import org.eclipse.edc.iam.did.spi.document.DidDocument;
import org.eclipse.edc.iam.did.spi.resolution.DidPublicKeyResolver;
import org.eclipse.edc.iam.did.spi.resolution.DidResolverRegistry;
import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.keys.KeyParserRegistryImpl;
import org.eclipse.edc.keys.spi.KeyParserRegistry;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(DependencyInjectionExtension.class)
class IssuerDataIdentityDidCoreExtensionTest {
    private static final String ISSUER_DID = "did:web:cs1.example.org%3A6183:issuer";

    @Test
    void shouldRegisterIssuerDataRegistryAndResolveKeysThroughIt(ServiceExtensionContext context, ObjectFactory factory) {
        context.registerService(KeyParserRegistry.class, new KeyParserRegistryImpl());
        context.registerService(Clock.class, Clock.systemUTC());
        var extension = factory.constructInstance(IssuerDataIdentityDidCoreExtension.class);

        extension.initialize(context);

        var registry = context.getService(DidResolverRegistry.class);
        assertThat(registry).isInstanceOf(IssuerDataDidResolverRegistry.class);
        var issuerResolver = mock(IssuerDidResolver.class);
        when(issuerResolver.getMethod()).thenReturn("web");
        when(issuerResolver.canResolve(ISSUER_DID)).thenReturn(true);
        when(issuerResolver.resolve(ISSUER_DID)).thenReturn(Result.success(DidDocument.Builder.newInstance().id(ISSUER_DID).build()));
        registry.register(issuerResolver);

        context.getService(DidPublicKeyResolver.class).resolveKey(ISSUER_DID + "#key-1");

        verify(issuerResolver).resolve(ISSUER_DID);
    }
}
