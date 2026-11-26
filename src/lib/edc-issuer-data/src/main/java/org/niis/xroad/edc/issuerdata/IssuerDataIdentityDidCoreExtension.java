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

import org.eclipse.edc.iam.did.resolution.DidPublicKeyResolverImpl;
import org.eclipse.edc.iam.did.spi.resolution.DidPublicKeyResolver;
import org.eclipse.edc.iam.did.spi.resolution.DidResolverRegistry;
import org.eclipse.edc.keys.spi.KeyParserRegistry;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import java.time.Clock;

/**
 * Provides the {@link DidResolverRegistry} and the {@link DidPublicKeyResolver} in place of the stock identity DID core
 * extension. The registry is an {@link IssuerDataDidResolverRegistry}, so a DID that the issuer resolver registered by
 * {@link IssuerDataExtension} can resolve does not use the registry cache. The stock settings for the DID cache keep
 * their keys and defaults.
 */
@Provides({DidResolverRegistry.class, DidPublicKeyResolver.class})
@Extension(IssuerDataIdentityDidCoreExtension.NAME)
public class IssuerDataIdentityDidCoreExtension implements ServiceExtension {

    static final String NAME = "X-Road Issuer Data Identity DID Core";

    @Setting(description = "Expiry time for caching DID Documents in milliseconds", key = "edc.did.resolver.cache.expiry",
            defaultValue = 1000 * 60 * 5 + "")
    private long didCacheExpiryMillis;

    @Setting(description = "The maximum number of cached DID Documents. 0 deactivates the cache",
            key = "edc.did.resolver.cache.size", defaultValue = "50")
    private int didCacheSize;

    @Inject
    private KeyParserRegistry keyParserRegistry;

    @Inject
    private Clock clock;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var didResolverRegistry = new IssuerDataDidResolverRegistry(didCacheSize, didCacheExpiryMillis, clock);
        context.registerService(DidResolverRegistry.class, didResolverRegistry);
        context.registerService(DidPublicKeyResolver.class,
                new DidPublicKeyResolverImpl(keyParserRegistry, didResolverRegistry));
    }
}
