/**
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
package org.niis.xroad.restapi.exceptions;

/**
 * Enumeration class for Management API error and warning codes
 */
public final class DeviationCodes {
    @Deprecated
    public static final String ERROR_GENERIC_INTERNAL_ERROR = "internal_error"; //TODO might be removed later
    public static final String ERROR_ID_NOT_A_NUMBER = "id_not_a_number";

    public static final String ERROR_ACCESSRIGHT_NOT_FOUND = "accessright_not_found";
    public static final String ERROR_ACTION_NOT_POSSIBLE = "action_not_possible";
    /**
     * Additional member already exists, when adding a new client
     */
    public static final String ERROR_ADDITIONAL_MEMBER_ALREADY_EXISTS = "additional_member_already_exists";
    public static final String ERROR_ANCHOR_EXISTS = "anchor_already_exists";
    public static final String ERROR_ANCHOR_FILE_NOT_FOUND = "anchor_file_not_found";
    public static final String ERROR_ANCHOR_NOT_FOR_EXTERNAL_SOURCE =
            "conf_verification.anchor_not_for_external_source";
    public static final String ERROR_ANCHOR_NOT_FOUND = "anchor_not_found";
    public static final String ERROR_ANCHOR_UPLOAD_FAILED = "anchor_upload_failed";
    public static final String ERROR_API_KEY_NOT_FOUND = "api_key_not_found";
    public static final String ERROR_AUTH_CERT_NOT_SUPPORTED = "auth_cert_not_supported";
    public static final String ERROR_BACKUP_FILE_NOT_FOUND = "backup_file_not_found";
    public static final String ERROR_BACKUP_GENERATION_FAILED = "backup_generation_failed";
    public static final String ERROR_BACKUP_DELETION_FAILED = "backup_deletion_failed";
    public static final String ERROR_BACKUP_RESTORE_INTERRUPTED = "backup_restore_interrupted";
    public static final String ERROR_BACKUP_RESTORE_PROCESS_FAILED = "restore_process_failed";
    public static final String ERROR_BASE_ENDPOINT_NOT_FOUND = "base_endpoint_not_found";
    public static final String ERROR_CANNOT_DELETE_OWNER = "cannot_delete_owner";
    public static final String ERROR_CANNOT_MAKE_OWNER = "member_already_owner";
    public static final String ERROR_CANNOT_REGISTER_OWNER = "cannot_register_owner";
    public static final String ERROR_CANNOT_UNREGISTER_OWNER = "cannot_unregister_owner";
    /**
     * Attempted to find CA certificate status and other details, but failed
     */
    public static final String ERROR_CA_CERT_PROCESSING = "ca_cert_status_processing_failure";
    public static final String ERROR_CA_NOT_FOUND = "certificate_authority_not_found";
    public static final String ERROR_CERTIFICATE_ALREADY_EXISTS = "certificate_already_exists";
    public static final String ERROR_CERTIFICATE_NOT_FOUND = "certificate_not_found";
    public static final String ERROR_CERTIFICATE_NOT_FOUND_WITH_ID = "certificate_id_not_found";
    public static final String ERROR_CERTIFICATE_WRONG_USAGE = "cert_wrong_usage";
    public static final String ERROR_CLIENT_ALREADY_EXISTS = "client_already_exists";
    public static final String ERROR_CLIENT_NOT_FOUND = "client_not_found";
    public static final String ERROR_CONF_DOWNLOAD_FAILED = "conf_download_failed";
    public static final String ERROR_CONF_VERIFICATION_OTHER = "conf_verification.other";
    public static final String ERROR_CONF_VERIFICATION_OUTDATED = "conf_verification.outdated";
    public static final String ERROR_CONF_VERIFICATION_SIGNATURE = "conf_verification.signature_invalid";
    public static final String ERROR_CONF_VERIFICATION_UNREACHABLE = "conf_verification.unreachable";
    public static final String ERROR_CSR_NOT_FOUND = "csr_not_found";
    public static final String ERROR_DIAGNOSTIC_REQUEST_FAILED = "diagnostic_request_failed";
    public static final String ERROR_DUPLICATE_ACCESSRIGHT = "duplicate_accessright";
    public static final String ERROR_DUPLICATE_CONFIGURED_TIMESTAMPING_SERVICE =
            "timestamping_service_already_configured";
    public static final String ERROR_DUPLICATE_LOCAL_GROUP_CODE = "local_group_code_already_exists";
    public static final String ERROR_ENDPOINT_NOT_FOUND = "endpoint_not_found";
    public static final String ERROR_EXISTING_ENDPOINT = "endpoint_already_exists";
    public static final String ERROR_EXISTING_SERVICE_CODE = "service_code_already_exists";
    public static final String ERROR_EXISTING_URL = "url_already_exists";
    public static final String ERROR_GENERATE_BACKUP_INTERRUPTED = "generate_backup_interrupted";
    public static final String ERROR_GLOBAL_CONF_DOWNLOAD_REQUEST = "global_conf_download_request_failed";
    public static final String ERROR_GPG_KEY_GENERATION_INTERRUPTED = "gpg_key_generation_interrupted";
    public static final String ERROR_GPG_KEY_GENERATION_FAILED = "gpg_key_generation_failed";
    public static final String ERROR_IDENTIFIER_NOT_FOUND = "identifier_not_found";
    public static final String ERROR_ILLEGAL_GENERATED_ENDPOINT_REMOVE = "illegal_generated_endpoint_remove";
    public static final String ERROR_ILLEGAL_GENERATED_ENDPOINT_UPDATE = "illegal_generated_endpoint_update";
    public static final String ERROR_INSTANTIATION_FAILED = "certificate_profile_instantiation_failure";
    public static final String ERROR_INTERNAL_ANCHOR_UPLOAD_INVALID_INSTANCE_ID =
            "internal_anchor_upload_invalid_instance_id";
    public static final String ERROR_INTERNAL_KEY_CERT_INTERRUPTED = "internal_key_cert_interrupted";
    public static final String ERROR_INVALID_BACKUP_FILE = "invalid_backup_file";
    public static final String ERROR_INVALID_CERT = "invalid_cert";
    public static final String ERROR_INVALID_CHARACTERS_PIN = "invalid_characters_pin";
    public static final String ERROR_INVALID_DISTINGUISHED_NAME = "invalid_distinguished_name";
    public static final String ERROR_INVALID_DN_PARAMETER = "invalid_dn_parameter";
    public static final String ERROR_INVALID_FILENAME = "invalid_filename";
    public static final String ERROR_INVALID_HTTPS_URL = "invalid_https_url";
    public static final String ERROR_INVALID_INIT_PARAMS = "invalid_init_params";
    public static final String ERROR_INVALID_INSTANCE_IDENTIFIER = "invalid_instance_identifier";
    public static final String ERROR_INVALID_MEMBER_CLASS = "invalid_member_class";
    public static final String ERROR_INVALID_SERVICE_IDENTIFIER = "invalid_wsdl_service_identifier";
    public static final String ERROR_INVALID_SERVICE_URL = "invalid_service_url";
    public static final String ERROR_INVALID_CONNECTION_TYPE = "invalid_connection_type";
    public static final String ERROR_INVALID_WSDL = "invalid_wsdl";
    public static final String ERROR_KEY_CERT_GENERATION_FAILED = "key_and_cert_generation_failed";
    public static final String ERROR_KEY_NOT_FOUND = "key_not_found";
    public static final String ERROR_LOCAL_GROUP_MEMBER_ALREADY_EXISTS = "local_group_member_already_exists";
    public static final String ERROR_LOCAL_GROUP_MEMBER_NOT_FOUND = "local_group_member_not_found";
    public static final String ERROR_LOCAL_GROUP_NOT_FOUND = "local_group_not_found";
    public static final String ERROR_MALFORMED_ANCHOR = "malformed_anchor";
    public static final String ERROR_MALFORMED_URL = "malformed_url";
    public static final String ERROR_MANAGEMENT_REQUEST_SENDING_FAILED = "management_request_sending_failed";
    public static final String ERROR_METADATA_INSTANCE_IDENTIFIER_EXISTS = "instance_identifier_exists";
    public static final String ERROR_METADATA_MEMBER_CLASS_EXISTS = "member_class_exists";
    public static final String ERROR_METADATA_MEMBER_CLASS_NOT_PROVIDED = "member_class_not_provided";
    public static final String ERROR_METADATA_MEMBER_CODE_EXISTS = "member_code_exists";
    public static final String ERROR_METADATA_MEMBER_CODE_NOT_PROVIDED = "member_code_not_provided";
    public static final String ERROR_METADATA_PIN_EXISTS = "pin_code_exists";
    public static final String ERROR_METADATA_PIN_MIN_CHAR_CLASSES = "pin_min_char_classes_count";
    public static final String ERROR_METADATA_PIN_MIN_LENGTH = "pin_min_length";
    public static final String ERROR_METADATA_PIN_NOT_PROVIDED = "pin_code_not_provided";
    public static final String ERROR_METADATA_SERVER_ADDRESS_EXISTS = "server_address_exists";
    public static final String ERROR_METADATA_SERVERCODE_EXISTS = "server_code_exists";
    public static final String ERROR_METADATA_SERVERCODE_NOT_PROVIDED = "server_code_not_provided";
    public static final String ERROR_MISSING_PARAMETER = "missing_parameter";
    public static final String ERROR_MISSING_PRIVATE_PARAMS = "conf_verification.missing_private_params";
    public static final String ERROR_OPENAPI_PARSING = "openapi_parsing_error";
    public static final String ERROR_ORPHANS_NOT_FOUND = "orphans_not_found";
    public static final String ERROR_OPENAPI_FILE_NOT_FOUND = "openapi_file_not_found";
    public static final String ERROR_OUTDATED_GLOBALCONF = "global_conf_outdated";
    public static final String ERROR_PIN_INCORRECT = "pin_incorrect";
    public static final String ERROR_PIN_POLICY_FAILURE = "pin_policy_failure";
    public static final String ERROR_RESOURCE_READ = "resource_read_failed";
    public static final String ERROR_SERVER_ALREADY_FULLY_INITIALIZED = "server_already_fully_initialized";
    public static final String ERROR_SERVICE_CLIENT_NOT_FOUND = "service_client_not_found";
    public static final String ERROR_SERVICE_EXISTS = "service_already_exists";
    public static final String ERROR_SERVICE_NOT_FOUND = "service_not_found";
    public static final String ERROR_SIGNER_NOT_REACHABLE = "signer_not_reachable";
    public static final String ERROR_SIGN_CERT_NOT_SUPPORTED = "sign_cert_not_supported";
    public static final String ERROR_SOFTWARE_TOKEN_INIT_FAILED = "software_token_init_failed";
    public static final String ERROR_TIMESTAMPING_SERVICE_NOT_FOUND = "timestamping_service_not_found";
    public static final String ERROR_TOKEN_NOT_ACTIVE = "token_not_active";
    public static final String ERROR_TOKEN_NOT_FOUND = "token_not_found";
    public static final String ERROR_VALIDATION_FAILURE = "validation_failure";
    public static final String ERROR_WARNINGS_DETECTED = "warnings_detected";
    public static final String ERROR_WEAK_PIN = "weak_pin";
    public static final String ERROR_WRONG_KEY_USAGE = "wrong_key_usage";
    public static final String ERROR_WRONG_TYPE = "wrong_servicedescription_type";
    public static final String ERROR_WSDL_DOWNLOAD_FAILED = "wsdl_download_failed";
    public static final String ERROR_WSDL_EXISTS = "wsdl_exists";
    public static final String ERROR_WSDL_VALIDATOR_INTERRUPTED = "wsdl_validator_interrupted";
    public static final String ERROR_WSDL_VALIDATOR_NOT_EXECUTABLE = "wsdl_validator_not_executable";
    public static final String ERROR_UNSUPPORTED_OPENAPI_VERSION = "unsupported_openapi_version";

    public static final String WARNING_ADDING_SERVICES = "adding_services";
    public static final String WARNING_AUTH_KEY_REGISTERED_CERT_DETECTED = "auth_key_with_registered_cert_warning";
    public static final String WARNING_DELETING_SERVICES = "deleting_services";
    public static final String WARNING_FILE_ALREADY_EXISTS = "warning_file_already_exists";
    public static final String WARNING_INIT_SERVER_ID_EXISTS = "init_server_id_exists";
    public static final String WARNING_INIT_UNREGISTERED_MEMBER = "init_unregistered_member";
    public static final String WARNING_INTERNAL_SERVER_SSL_ERROR = "internal_server_ssl_error";
    public static final String WARNING_INTERNAL_SERVER_SSL_HANDSHAKE_ERROR = "internal_server_ssl_handshake_error";
    public static final String WARNING_OPENAPI_VALIDATION_WARNINGS = "openapi_validation_warnings";
    public static final String WARNING_SERVERCODE_EXISTS = "init_servercode_exists";
    public static final String WARNING_SERVER_OWNER_EXISTS = "init_server_owner_exists";
    public static final String WARNING_SOFTWARE_TOKEN_INITIALIZED = "init_software_token_initialized";
    public static final String WARNING_UNREGISTERED_MEMBER = "unregistered_member";
    public static final String WARNING_WSDL_VALIDATION_WARNINGS = "wsdl_validation_warnings";

    private DeviationCodes() {
        // noop
    }
}
