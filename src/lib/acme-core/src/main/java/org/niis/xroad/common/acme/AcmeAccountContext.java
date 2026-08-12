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
package org.niis.xroad.common.acme;

import java.security.KeyPair;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Everything {@link AcmeClient} needs to act as a given ACME account: which CA, which key pair identifies the
 * account, and the optional extras some CAs require. Carries no member identifier and no signer key-usage type -
 * callers resolve those themselves and only pass in what the ACME protocol itself needs.
 *
 * @param directoryUrl         the CA's ACME directory URL, as a plain {@code http(s)} URL
 * @param accountKeyPair       key pair identifying the ACME account; the caller owns its storage and lifecycle
 * @param eabCredentials       supplies External Account Binding credentials, or {@code null} if the CA needs none.
 *                             A supplier rather than a resolved value because whether it is even consulted depends
 *                             on the CA (new-account calls only resolve it once the CA's own metadata says EAB is
 *                             required), and resolution itself may fail (e.g. no credentials configured yet) - that
 *                             failure should surface only when EAB is actually needed, exactly as before extraction.
 * @param contactUri           account contact URI (e.g. {@code mailto:...}), or {@code null} if none is supplied
 * @param certificateProfileId CA-side certificate profile identifier, or {@code null} if the CA needs none.
 *                             Only consulted when placing a new order; renewal and ARI calls never send it, matching
 *                             the CA's own scoping of the underlying custom ACME connection.
 */
public record AcmeAccountContext(
        String directoryUrl,
        KeyPair accountKeyPair,
        Supplier<AcmeEabCredentials> eabCredentials,
        String contactUri,
        String certificateProfileId) {

    public AcmeAccountContext {
        Objects.requireNonNull(directoryUrl, "directoryUrl must not be null");
        Objects.requireNonNull(accountKeyPair, "accountKeyPair must not be null");
    }
}
