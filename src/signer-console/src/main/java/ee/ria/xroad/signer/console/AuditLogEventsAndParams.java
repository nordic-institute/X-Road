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
package ee.ria.xroad.signer.console;

/**
 * Audit log events and params for the signer-console.
 */
final class AuditLogEventsAndParams {

    static final String SET_A_FRIENDLY_NAME_TO_THE_TOKEN_EVENT =
            "Set a friendly name to the token";
    static final String SET_A_FRIENDLY_NAME_TO_THE_KEY_EVENT =
            "Set a friendly name to the key";
    static final String ACTIVATE_THE_CERTIFICATE_EVENT =
            "Activate the certificate";
    static final String DEACTIVATE_THE_CERTIFICATE_EVENT =
            "Deactivate the certificate";
    // NB! Key deletion from token is supported only.
    static final String DELETE_THE_KEY_EVENT = "Delete the key from token";
    static final String DELETE_THE_CERT_EVENT = "Delete the certificate";
    static final String DELETE_THE_CERT_REQUEST_EVENT =
            "Delete the certificate request";
    static final String LOG_INTO_THE_TOKEN = "Log into the token";
    static final String UPDATE_SOFTWARE_TOKEN_PIN = "Update software token PIN";
    static final String LOGOUT_FROM_THE_TOKEN_EVENT =
            "Logout from the token";
    static final String INITIALIZE_THE_SOFTWARE_TOKEN_EVENT =
            "Initialize the software token";
    static final String GENERATE_A_KEY_ON_THE_TOKEN_EVENT =
            "Generate a key on the token";
    static final String GENERATE_A_CERT_REQUEST_EVENT =
            "Generate CSR";
    static final String IMPORT_A_CERTIFICATE_FROM_THE_FILE =
            "Import a certificate from the file";

    static final String TOKEN_ID_PARAM = "tokenId";
    static final String TOKEN_FRIENDLY_NAME_PARAM = "tokenFriendlyName";
    static final String KEY_ID_PARAM = "keyId";
    static final String KEY_LABEL_PARAM = "keyLabel";
    static final String KEY_FRIENDLY_NAME_PARAM = "keyFriendlyName";
    static final String CERT_ID_PARAM = "certId";
    static final String CERT_REQUEST_ID_PARAM = "certRequestId";
    static final String KEY_USAGE_PARAM = "keyUsage";
    static final String CLIENT_IDENTIFIER_PARAM = "clientIdentifier";
    static final String SUBJECT_NAME_PARAM = "subjectName";
    static final String CERT_FILE_NAME_PARAM = "certFileName";
    static final String CSR_FORMAT_PARAM = "csrFormat";

    private AuditLogEventsAndParams() {
    }
}
