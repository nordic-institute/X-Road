/*
 * The MIT License
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

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.core.exception.DeviationBuilder;

@RequiredArgsConstructor
public enum AcmeDeviationMessage implements DeviationBuilder.ErrorDeviationBuilder {

    EAB_CREDENTIALS_MISSING("acme.eab_credentials_missing"),
    EAB_SECRET_LENGTH("acme.eab_secret_length"),
    ACCOUNT_KEY_PAIR_ERROR("acme.account_key_pair_error"),
    ACCOUNT_KEYSTORE_PASSWORD_MISSING("acme.account_keystore_password_missing"),
    ACME_YAML_MISSING("acme.acme_yaml_missing"),
    ACME_YAML_ACCOUNT_KEYSTORE_PASSWORD_UPDATE_ERROR("acme.acme_yaml_account_keystore_password_update_error"),
    FETCHING_METADATA_ERROR("acme.fetching_metadata_error"),
    ACCOUNT_CREATION_FAILURE("acme.account_creation_failure"),
    ORDER_CREATION_FAILURE("acme.order_creation_failure"),
    ORDER_FINALIZATION_FAILURE("acme.order_finalization_failure"),
    HTTP_CHALLENGE_FILE_CREATION("acme.http_challenge_file_creation"),
    HTTP_CHALLENGE_FILE_DELETION("acme.http_challenge_file_deletion"),
    HTTP_CHALLENGE_MISSING("acme.http_challenge_missing"),
    CHALLENGE_TRIGGER_FAILURE("acme.challenge_trigger_failure"),
    AUTHORIZATION_FAILURE("acme.authorization_failure"),
    AUTHORIZATION_WAIT_FAILURE("acme.authorization_wait_failure"),
    CERTIFICATE_FAILURE("acme.certificate_failure"),
    CERTIFICATE_WAIT_FAILURE("acme.certificate_wait_failure"),
    FETCHING_RENEWAL_INFO_FAILURE("acme.fetching_renewal_info_failure");

    private final String code;

    @Override
    public String code() {
        return code;
    }


}
