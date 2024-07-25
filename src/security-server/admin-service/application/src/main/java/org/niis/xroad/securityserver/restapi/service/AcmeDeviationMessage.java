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
package org.niis.xroad.securityserver.restapi.service;

import lombok.Getter;
import org.niis.xroad.restapi.exceptions.DeviationProvider;

public enum AcmeDeviationMessage implements DeviationProvider {

    EAB_CREDENTIALS_MISSING("acme.eab_credentials_missing", "External Account binding credentials missing, but required"),
    EAB_SECRET_LENGTH("acme.eab_secret_length", "Invalid external account binding base64 secret length"),
    ACCOUNT_KEY_PAIR_ERROR("acme.account_key_pair_error", "Getting key pair for ACME server account failed"),
    FETCHING_METADATA_ERROR("acme.fetching_metadata_error", "Fetching ACME server metadata failed. ACME Server might be unreachable"),
    ACCOUNT_CREATION_FAILURE("acme.account_creation_failure",
            "Creating Account on ACME server failed. If external account binding is required, check that the correct credentials are "
                    + "configured in acme.yml"),
    ORDER_CREATION_FAILURE("acme.order_creation_failure", "Creating new Order on ACME server failed"),
    ORDER_FINALIZATION_FAILURE("acme.order_finalization_failure", "Finalizing the Order on ACME server failed"),
    HTTP_CHALLENGE_FILE_CREATION("acme.http_challenge_file_creation", "Creating ACME HTTP challenge file failed"),
    HTTP_CHALLENGE_FILE_DELETION("acme.http_challenge_file_deletion", "Deleting ACME HTTP challenge file failed"),
    HTTP_CHALLENGE_MISSING("acme.http_challenge_missing",
            "Currently X-Road only supports HTTP challenge, but is missing from the possible options from the ACME server"),
    CHALLENGE_TRIGGER_FAILURE("acme.challenge_trigger_failure", "Requesting the ACME server to validate the challenge failed"),
    AUTHORIZATION_FAILURE("acme.authorization_failure",
            "Authorization failed. Usually related to a challenge validation failure that is required to prove the "
                    + "ownership of the domain"),
    AUTHORIZATION_WAIT_FAILURE("acme.authorization_wait_failure", "Waiting for the Authorization to complete failed"),
    CERTIFICATE_FAILURE("acme.certificate_failure", "Getting the Certificate from the ACME server failed"),
    CERTIFICATE_WAIT_FAILURE("acme.certificate_wait_failure", "Waiting for the creation of the new Certificate failed");

    @Getter
    private final String code;
    @Getter
    private final String description;

    AcmeDeviationMessage(final String code, final String description) {
        this.code = code;
        this.description = description;
    }

}
