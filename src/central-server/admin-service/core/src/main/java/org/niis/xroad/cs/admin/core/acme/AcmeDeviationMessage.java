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
package org.niis.xroad.cs.admin.core.acme;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.core.exception.DeviationBuilder;

/**
 * Deviations for the ACME account keystore the Central Server keeps on top of the shared ACME core, and for
 * EAB-configuration lookup concerns the core knows nothing about. Deviations for the ACME protocol itself live in
 * {@link org.niis.xroad.common.acme.AcmeDeviationMessage}.
 */
@RequiredArgsConstructor
public enum AcmeDeviationMessage implements DeviationBuilder.ErrorDeviationBuilder {

    EAB_CREDENTIALS_MISSING("acme.eab_credentials_missing"),
    ACCOUNT_KEY_PAIR_ERROR("acme.account_key_pair_error"),
    ACCOUNT_KEYSTORE_PASSWORD_MISSING("acme.account_keystore_password_missing");

    private final String code;

    @Override
    public String code() {
        return code;
    }

}
