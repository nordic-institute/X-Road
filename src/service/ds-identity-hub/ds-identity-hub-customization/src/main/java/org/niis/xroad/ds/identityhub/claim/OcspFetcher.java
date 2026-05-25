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
package org.niis.xroad.ds.identityhub.claim;

import ee.ria.xroad.common.util.CryptoUtils;
import org.eclipse.edc.spi.result.Result;
import org.niis.xroad.signer.client.SignerRpcClient;

import java.security.cert.X509Certificate;
import java.util.Base64;

/**
 * Fetches a cached OCSP response for the holder's sign certificate from the X-Road
 * signer service over gRPC.
 *
 * <p>The signer keeps a periodically-refreshed OCSP cache for the keys it manages — same
 * mechanism that pins OCSP responses onto SOAP signatures. We pull the latest cached
 * response by cert hash and embed it in the JWS {@code ocsp} header so the issuer can
 * validate offline.
 *
 * <p>Cold-start: if the signer's first periodic OCSP fetch hasn't completed (cache empty
 * for this cert), the response slot is {@code null}. We surface this as a failure rather
 * than emitting an unsigned-OCSP JWS — the verifier (slice 03 design + slice 05 wiring)
 * rejects JWS missing the {@code ocsp} header.
 */
final class OcspFetcher {

    private final SignerRpcClient signerRpcClient;

    OcspFetcher(SignerRpcClient signerRpcClient) {
        this.signerRpcClient = signerRpcClient;
    }

    /**
     * @return DER bytes of the OCSP response for {@code cert}, or a failure if no
     * cached response is available.
     */
    Result<byte[]> fetch(X509Certificate cert) {
        String certHash;
        try {
            certHash = CryptoUtils.calculateCertHexHash(cert);
        } catch (Exception e) {
            return Result.failure("cannot compute cert hash: " + e.getMessage());
        }
        String[] responses;
        try {
            responses = signerRpcClient.getOcspResponses(new String[]{certHash});
        } catch (Exception e) {
            return Result.failure("signer-service: OCSP fetch failed: " + e.getMessage());
        }
        if (responses == null || responses.length == 0 || responses[0] == null) {
            return Result.failure("signer-service: no cached OCSP response for cert hash " + certHash);
        }
        try {
            return Result.success(Base64.getDecoder().decode(responses[0]));
        } catch (IllegalArgumentException e) {
            return Result.failure("signer-service: malformed OCSP response (not base64): " + e.getMessage());
        }
    }
}
