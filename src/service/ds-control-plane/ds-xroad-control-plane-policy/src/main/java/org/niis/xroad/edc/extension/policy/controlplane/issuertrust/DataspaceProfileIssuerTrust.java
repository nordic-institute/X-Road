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
package org.niis.xroad.edc.extension.policy.controlplane.issuertrust;

import org.eclipse.edc.protocol.spi.DataspaceProfileContext;
import org.eclipse.edc.protocol.spi.DataspaceProfileContextRegistry;
import org.eclipse.edc.protocol.spi.TrustedIssuer;
import org.eclipse.edc.spi.EdcException;

import java.util.List;
import java.util.Set;

import static org.eclipse.edc.protocol.spi.TrustedIssuer.WILDCARD_TYPE;

/**
 * Keeps the trusted issuers of every registered dataspace profile in step with the given issuer DIDs.
 *
 * <p>A profile's trusted issuers are set once, when the profile is registered, so each call registers a copy of
 * every active profile that differs from the original only in its trusted issuers. EDC reads the profile from
 * the registry on every request, so the new trust applies from the next request.
 */
final class DataspaceProfileIssuerTrust implements DataspaceIssuerTrust {

    private final DataspaceProfileContextRegistry profileRegistry;

    DataspaceProfileIssuerTrust(DataspaceProfileContextRegistry profileRegistry) {
        this.profileRegistry = profileRegistry;
    }

    @Override
    public void trustOnly(Set<String> issuerDids) {
        var profiles = profileRegistry.getProfiles();
        if (profiles.isEmpty()) {
            throw new EdcException("No dataspace profile is registered, issuer trust cannot be applied");
        }

        var trustedIssuers = issuerDids.stream().map(DataspaceProfileIssuerTrust::trustedForAllCredentialTypes).toList();
        profiles.stream()
                .map(profile -> withTrustedIssuers(profile, trustedIssuers))
                .forEach(profileRegistry::register);
    }

    private static TrustedIssuer trustedForAllCredentialTypes(String issuerDid) {
        return TrustedIssuer.Builder.newInstance()
                .id(issuerDid)
                .supportedTypes(List.of(WILDCARD_TYPE))
                .build();
    }

    private static DataspaceProfileContext withTrustedIssuers(DataspaceProfileContext profile, List<TrustedIssuer> trustedIssuers) {
        return new DataspaceProfileContext(
                profile.name(),
                profile.protocolVersion(),
                profile.webhook(),
                profile.idExtractionFunction(),
                profile.protocolNamespace(),
                profile.jsonLdContextsUrl(),
                trustedIssuers);
    }
}
