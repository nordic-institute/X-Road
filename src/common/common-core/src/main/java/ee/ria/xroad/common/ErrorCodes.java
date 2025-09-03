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
package ee.ria.xroad.common;

import org.niis.xroad.common.core.exception.XrdRuntimeException;

/**
 * Enumeration class for various error codes.
 */
public final class ErrorCodes {

    // Error code prefixes

    public static final String SERVER_SERVERPROXY_X = "server.serverproxy";
    public static final String CLIENT_X = "client";
    public static final String SERVER_CLIENTPROXY_X = "server.clientproxy";
    public static final String SIGNER_X = "signer";
    public static final String SERVER_SERVER_PROXY_OPMONITOR_X =
            SERVER_SERVERPROXY_X + ".opmonitor";

    // Generic errors.

    public static final String X_IO_ERROR = "io_error";
    public static final String X_NETWORK_ERROR = "network_error";
    public static final String X_INTERNAL_ERROR = "internal_error";
    public static final String X_BAD_REQUEST = "bad_request";
    public static final String X_NOT_FOUND = "not_found";
    public static final String X_HTTP_ERROR = "http_error";
    public static final String X_DATABASE_ERROR = "database_error";
    public static final String X_INVALID_REQUEST = "invalid_request";


    // Verification errors

    public static final String X_CANNOT_CREATE_SIGNATURE =
            "cannot_create_signature";
    public static final String X_CERT_VALIDATION = "cert_validation";
    public static final String X_INCORRECT_VALIDATION_INFO =
            "incorrect_validation_info";
    public static final String X_CANNOT_CREATE_CERT_PATH =
            "cannot_create_cert_path";
    public static final String X_INCORRECT_CERTIFICATE = "incorrect_certificate";
    public static final String X_INVALID_SIGNATURE_VALUE = "invalid_signature_value";
    public static final String X_MALFORMED_SIGNATURE = "malformed_signature";
    public static final String X_INVALID_XML = "invalid_xml";
    public static final String X_INVALID_REFERENCE = "invalid_reference";
    public static final String X_INVALID_CERT_PATH_X = "invalid_cert_path";
    public static final String X_SIGNATURE_VERIFICATION_X =
            "signature_verification";
    public static final String X_TIMESTAMP_VALIDATION = "timestamp_validation";
    public static final String X_INVALID_HASH_CHAIN_RESULT = "invalid_hash_chain";
    public static final String X_MALFORMED_HASH_CHAIN = "malformed_hash_chain";
    public static final String X_HASHCHAIN_UNUSED_INPUTS = "hashchain_unused_inputs";
    public static final String X_INVALID_HASH_CHAIN_REF = "invalid_hash_chain_ref";

    // Message processing errors

    public static final String X_SSL_AUTH_FAILED = "ssl_authentication_failed";
    public static final String X_LOGGING_FAILED_X = "logging_failed";
    public static final String X_TIMESTAMPING_FAILED_X = "timestamping_failed";
    public static final String X_INVALID_CONTENT_TYPE = "invalid_content_type";
    public static final String X_INVALID_SOAPACTION = "invalid_soap_action";
    public static final String X_INVALID_HTTP_METHOD = "invalid_http_method";
    public static final String X_INVALID_MESSAGE = "invalid_message";
    public static final String X_INVALID_SECURITY_SERVER = "invalid_security_server";
    public static final String X_MIME_PARSING_FAILED = "mime_parsing_failed";
    public static final String X_MISSING_HEADER = "missing_header";
    public static final String X_MISSING_HEADER_FIELD = "missing_header_field";
    public static final String X_DUPLICATE_HEADER_FIELD = "duplicate_header_field";
    public static final String X_MISSING_BODY = "missing_body";
    public static final String X_INVALID_BODY = "invalid_body";
    public static final String X_INCONSISTENT_HEADERS = "inconsistent_headers";
    public static final String X_INCONSISTENT_RESPONSE = "inconsistent_response";
    public static final String X_MISSING_SOAP = "missing_soap";
    public static final String X_INVALID_SOAP = "invalid_soap";
    public static final String X_MISSING_REST = "missing_rest";
    public static final String X_ACCESS_DENIED = "access_denied";
    public static final String X_SERVICE_DISABLED = "service_disabled";
    public static final String X_SERVICE_FAILED_X = "service_failed";
    public static final String X_MISSING_SIGNATURE = "missing_signature";
    public static final String X_UNKNOWN_SERVICE = "unknown_service";
    public static final String X_INVALID_PROTOCOL_VERSION =
            "invalid_protocol_version";
    public static final String X_INVALID_CLIENT_IDENTIFIER =
            "invalid_client_identifier";
    public static final String X_INVALID_SERVICE_TYPE = "service_type";
    public static final String X_CLIENT_PROXY_VERSION_NOT_SUPPORTED = "client_proxy_version_not_supported";

    // ASiC container related errors

    public static final String X_ASIC_MIME_TYPE_NOT_FOUND = "asic_mime_type_not_found";
    public static final String X_ASIC_SIGNATURE_NOT_FOUND = "asic_signature_not_found";
    public static final String X_ASIC_MESSAGE_NOT_FOUND = "asic_message_not_found";
    public static final String X_ASIC_INVALID_MIME_TYPE = "asic_invalid_mime_type";
    public static final String X_ASIC_HASH_CHAIN_RESULT_NOT_FOUND =
            "asic_hash_chain_result_not_found";
    public static final String X_ASIC_HASH_CHAIN_NOT_FOUND =
            "asic_hash_chain_not_found";
    public static final String X_ASIC_TIMESTAMP_NOT_FOUND =
            "asic_timestamp_not_found";
    public static final String X_ASIC_MANIFEST_NOT_FOUND =
            "asic_manifest_not_found";

    // Configuration errors

    public static final String X_UNKNOWN_MEMBER = "unknown_member";
    public static final String X_MALFORMED_SERVERCONF = "malformed_server_conf";
    public static final String X_MALFORMED_GLOBALCONF = "malformed_global_conf";
    public static final String X_MALFORMED_OPTIONAL_PARTS_CONF = "malformed_optional_parts_conf";
    public static final String X_OUTDATED_GLOBALCONF = "outdated_global_conf";
    public static final String X_SERVICE_MISSING_URL = "service_missing_url";
    public static final String X_SERVICE_MALFORMED_URL = "service_malformed_url";
    public static final String X_HW_MODULE_NON_OPERATIONAL = "hsm_non_operational";
    public static final String X_MAINTENANCE_MODE = "maintenance_mode";

    // Signer Errors

    public static final String X_KEY_NOT_FOUND = "key_not_found";
    public static final String X_CERT_NOT_FOUND = "cert_not_found";
    public static final String X_CSR_NOT_FOUND = "csr_not_found";
    public static final String X_TOKEN_NOT_FOUND = "token_not_found";
    public static final String X_TOKEN_NOT_ACTIVE = "token_not_active";
    public static final String X_TOKEN_NOT_INITIALIZED = "token_not_initialized";
    public static final String X_TOKEN_NOT_AVAILABLE = "token_not_available";
    public static final String X_TOKEN_READONLY = "token_readonly";
    public static final String X_CANNOT_SIGN = "cannot_sign";
    public static final String X_UNSUPPORTED_SIGN_ALGORITHM = "unsupported_sign_algorithm";
    public static final String X_FAILED_TO_GENERATE_R_KEY =
            "failed_to_generate_private_key";
    public static final String X_CERT_EXISTS = "cert_exists";
    public static final String X_WRONG_CERT_USAGE = "wrong_cert_usage";
    public static final String X_LOGIN_FAILED = "login_failed";
    public static final String X_LOGOUT_FAILED = "logout_failed";
    public static final String X_PIN_INCORRECT = "pin_incorrect";
    public static final String X_CERT_IMPORT_FAILED = "cert_import_failed";
    public static final String X_TOKEN_PIN_POLICY_FAILURE = "token_pin_policy_failure";

    // MessageLog errors
    public static final String X_MLOG_TIMESTAMPER_FAILED = "timestamper_failed";

    /**
     * Translates technical exceptions to proxy exceptions with
     * the appropriate error code.
     *
     * @param ex the exception
     * @return translated CodedException
     */
    public static CodedException  translateException(Throwable ex) {
        return XrdRuntimeException.systemException(ex);
    }

    /**
     * Translates technical exceptions to proxy exceptions with
     * the appropriate error code. It also prepends the prefix
     * in front of error code.
     *
     * @param prefix the prefix
     * @param ex     the exception
     * @return translated exception with prefix
     */
    public static CodedException translateWithPrefix(String prefix,
                                                     Throwable ex) {
        return translateException(ex).withPrefix(prefix);
    }

    private ErrorCodes() {
    }
}
