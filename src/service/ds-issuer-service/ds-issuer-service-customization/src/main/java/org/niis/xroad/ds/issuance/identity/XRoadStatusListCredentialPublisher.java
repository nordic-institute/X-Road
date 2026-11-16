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
import org.eclipse.edc.identityhub.spi.verifiablecredentials.model.VerifiableCredentialResource;
import org.eclipse.edc.issuerservice.spi.credentials.statuslist.StatusListCredentialPublisher;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.result.Result;

import static org.eclipse.edc.issuerservice.spi.credentials.statuslist.StatusListCredentialUrl.createUrl;
import static org.niis.xroad.ds.issuance.identity.XRoadStatusListCredentialPublisherExtension.NAME;

/**
 * Publishes a status list (revocation list) credential under the statuslist base URL the Central Server
 * operator bound, read live from a {@link BoundIssuerIdentityHolder} on every call. Unlike EDC's own {@code
 * LocalCredentialPublisher}, which keeps an immutable base URL read once at boot from {@code
 * edc.statuslist.callback.address}, this publisher applies a binding that lands after boot without a restart.
 * While unbound, every call is refused instead of publishing under a wrong or missing address.
 *
 * <p>EDC's {@code BitstringStatusListManager} calls {@link #publicUrl(String)} first when it creates a new
 * list, to mint the credential ID the new list is built and signed around, before it stores anything; this
 * refusal throwing there blocks creation outright, with no partially-built list left behind. Renewing an
 * existing list keeps its stored URL and calls only {@link #publish(VerifiableCredentialResource)}, after the
 * renewed bitstring is computed in memory but before anything is stored; refusing there leaves the stored list
 * untouched.
 */
@RequiredArgsConstructor
public class XRoadStatusListCredentialPublisher implements StatusListCredentialPublisher {

    private final BoundIssuerIdentityHolder holder;

    @Override
    public Result<String> publish(VerifiableCredentialResource verifiableCredentialResource) {
        if (holder.identity().isEmpty()) {
            return Result.failure(NAME + ": the issuer identity is not bound yet");
        }
        return Result.success(verifiableCredentialResource.getVerifiableCredential().credential().getId());
    }

    @Override
    public String publicUrl(String credentialId) {
        return holder.identity()
                .map(identity -> createUrl(identity.statuslistBaseUrl(), credentialId))
                .orElseThrow(() -> new EdcException(NAME + ": the issuer identity is not bound yet"));
    }
}
