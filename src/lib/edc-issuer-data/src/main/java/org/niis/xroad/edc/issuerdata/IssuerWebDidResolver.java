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

import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import org.eclipse.edc.iam.did.spi.document.DidDocument;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.niis.xroad.globalconf.GlobalConfProvider;

/**
 * Resolves the DIDs of the credential issuers of the own instance through the {@link LastGoodSource}, so the DID
 * document is served from the last good copy while the Issuer Service does not answer. The registry gives it only
 * {@code did:web} DIDs. It resolves no other DID: it makes no download and stores no row for it.
 * <p>
 * A DID document that cannot be downloaded and has no usable copy gives the failure of the download, which is
 * retryable when the issuer did not answer.
 */
@RequiredArgsConstructor
public class IssuerWebDidResolver implements IssuerDidResolver {
    private static final String DID_METHOD = "web";

    private final LastGoodSource<DidDocument> source;
    private final GlobalConfProvider globalConf;
    private final Monitor monitor;

    @Override
    @Nonnull
    public String getMethod() {
        return DID_METHOD;
    }

    @Override
    @Nonnull
    public Result<DidDocument> resolve(String didKey) {
        if (!canResolve(didKey)) {
            return Result.failure("%s is not a credential issuer DID of the own instance".formatted(didKey));
        }
        var copy = source.lookup(didKey);
        return copy.succeeded() ? Result.success(copy.getContent().parsed()) : copy.mapFailure();
    }

    @Override
    public boolean canResolve(String didKey) {
        try {
            return globalConf.getCredentialIssuerDids(globalConf.getInstanceIdentifier()).contains(didKey);
        } catch (RuntimeException e) {
            monitor.warning("Global configuration is not readable, %s is not resolved as an issuer DID: %s"
                    .formatted(didKey, e.getMessage()));
            return false;
        }
    }
}
