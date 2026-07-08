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
package org.niis.xroad.ss.test.api;

import org.niis.xroad.test.framework.core.token.TestJwtSigner;

import java.util.Map;

final class DspAuthTokens {

    private static final String PRIVATE_KEY_RESOURCE = "/container-files/jwks/private_key.json";
    private static final TestJwtSigner SIGNER = new TestJwtSigner(PRIVATE_KEY_RESOURCE);

    static final String IS_PROVISIONER = bearer(SIGNER.sign(null, Map.of(
            "scope", "identity-api:admin issuer-admin-api:write issuer-admin-api:read"
    )));

    static final String IS_PARTICIPANT = bearer(SIGNER.sign(null, Map.of(
            "scope", "issuer-admin-api:admin"
    )));

    static final String IH_PROVISIONER = bearer(SIGNER.sign(null, Map.of(
            "scope", "identity-api:admin"
    )));

    static final String IH_ADMIN = bearer(SIGNER.sign(null, Map.of(
            "scope", "identity-api:admin"
    )));

    static final String CP_PROVISIONER = bearer(SIGNER.sign(null, Map.of(
            "scope", "management-api:admin"
    )));

    private DspAuthTokens() {
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
