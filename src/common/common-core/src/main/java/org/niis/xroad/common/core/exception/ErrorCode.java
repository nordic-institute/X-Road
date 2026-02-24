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
package org.niis.xroad.common.core.exception;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Enumeration of all X-Road error codes.
 */
@Slf4j
@RequiredArgsConstructor
public enum ErrorCode implements DeviationBuilder.ErrorDeviationBuilder {

    // ===== GENERIC ERRORS =====
    IO_ERROR("io_error"),
    NETWORK_ERROR("network_error"),
    INTERNAL_ERROR("internal_error"),
    BAD_REQUEST("bad_request"),
    NOT_FOUND("not_found"),
    HTTP_ERROR("http_error"),
    UNKNOWN_HOST("unknown_host"),
    DATABASE_ERROR("database_error"),
    INVALID_RESPONSE("invalid_response"),
    INVALID_REQUEST("invalid_request"),
    CRYPTO_ERROR("cryptography_error"),
    // ===== VERIFICATION ERRORS =====
    CANNOT_CREATE_SIGNATURE("cannot_create_signature"),
    CERT_VALIDATION("cert_validation"),
    MISSING_VALIDATION_INFO("missing_validation_info"),
    INCORRECT_VALIDATION_INFO("incorrect_validation_info"),
    CANNOT_CREATE_CERT_PATH("cannot_create_cert_path"),
    CERT_VALIDITY_TIME("cert_validity_time"),
    INCORRECT_CERTIFICATE("incorrect_certificate"),
    UNSUPPORTED_ALGORITHM("unsupported_algorithm"),
    INVALID_SIGNATURE_VALUE("invalid_signature_value"),
    MALFORMED_SIGNATURE("malformed_signature"),
    INVALID_XML("invalid_xml"),
    INVALID_REFERENCE("invalid_reference"),
    INVALID_CERT_PATH("invalid_cert_path"),
    SIGNATURE_VERIFICATION("signature_verification"),
    MALFORMED_SOAP("malformed_soap"),
    INVALID_HASH_CHAIN_RESULT("invalid_hash_chain"),
    MALFORMED_HASH_CHAIN("malformed_hash_chain"),
    HASHCHAIN_UNUSED_INPUTS("hashchain_unused_inputs"),
    INVALID_HASH_CHAIN_REF("invalid_hash_chain_ref"),
    INVALID_ENCODED_ID("invalid_encoded_id"),
    GENERIC_VALIDATION_FAILURE("invalid_parameters"),
    ERROR_ID_NOT_A_NUMBER("id_not_a_number"),
    CERTIFICATE_ALREADY_EXISTS("certificate_already_exists"),
    USER_WEAK_PASSWORD("user_weak_password"),
    USER_PASSWORD_INVALID_CHARACTERS("user_password_invalid_characters"),
    USER_NOT_FOUND("user_not_found"),
    INVALID_ROLE("invalid_role"),
    PASSWORD_INCORRECT("password_incorrect"),

    // ===== MESSAGE PROCESSING ERRORS =====
    SSL_AUTH_FAILED("ssl_authentication_failed"),
    LOGGING_FAILED("logging_failed"),
    TIMESTAMPING_FAILED("timestamping_failed"),
    INVALID_CONTENT_TYPE("invalid_content_type"),
    INVALID_SOAP_ACTION("invalid_soap_action"),
    INVALID_HTTP_METHOD("invalid_http_method"),
    INVALID_MESSAGE("invalid_message"),
    INVALID_SECURITY_SERVER("invalid_security_server"),
    MIME_PARSING_FAILED("mime_parsing_failed"),
    MISSING_HEADER("missing_header"),
    MISSING_HEADER_FIELD("missing_header_field"),
    DUPLICATE_HEADER_FIELD("duplicate_header_field"),
    MISSING_BODY("missing_body"),
    INVALID_BODY("invalid_body"),
    INCONSISTENT_HEADERS("inconsistent_headers"),
    INCONSISTENT_RESPONSE("inconsistent_response"),
    MISSING_SOAP("missing_soap"),
    INVALID_SOAP("invalid_soap"),
    MISSING_REST("missing_rest"),
    INVALID_REST("invalid_rest"),
    ACCESS_DENIED("access_denied"),
    SERVICE_DISABLED("service_disabled"),
    SERVICE_FAILED("service_failed"),
    MISSING_SIGNATURE("missing_signature"),
    UNKNOWN_SERVICE("unknown_service"),
    INVALID_PROTOCOL_VERSION("invalid_protocol_version"),
    INVALID_CLIENT_IDENTIFIER("invalid_client_identifier"),
    INVALID_SERVICE_TYPE("service_type"),
    CLIENT_PROXY_VERSION_NOT_SUPPORTED("client_proxy_version_not_supported"),

    // ===== ASIC CONTAINER RELATED ERRORS =====
    ASIC_INVALID_CONTAINER("asic_invalid_container"),
    ASIC_MIME_TYPE_NOT_FOUND("asic_mime_type_not_found"),
    ASIC_SIGNATURE_NOT_FOUND("asic_signature_not_found"),
    ASIC_MESSAGE_NOT_FOUND("asic_message_not_found"),
    ASIC_INVALID_MIME_TYPE("asic_invalid_mime_type"),
    ASIC_HASH_CHAIN_RESULT_NOT_FOUND("asic_hash_chain_result_not_found"),
    ASIC_HASH_CHAIN_NOT_FOUND("asic_hash_chain_not_found"),
    ASIC_TIMESTAMP_NOT_FOUND("asic_timestamp_not_found"),
    ASIC_MANIFEST_NOT_FOUND("asic_manifest_not_found"),

    // ===== CONFIGURATION ERRORS =====
    UNKNOWN_MEMBER("unknown_member"),
    MALFORMED_SERVERCONF("malformed_server_conf"),
    MALFORMED_KEYCONF("malformed_key_conf"),
    MALFORMED_GLOBALCONF("malformed_global_conf"),
    MALFORMED_OPTIONAL_PARTS_CONF("malformed_optional_parts_conf"),
    MISSING_GLOBALCONF("malformed_global_conf"),
    SERVICE_MISSING_URL("service_missing_url"),
    SERVICE_MALFORMED_URL("service_malformed_url"),
    ADAPTER_WSDL_NOT_FOUND("adapter_wsdl_not_found"),
    HW_MODULE_NON_OPERATIONAL("hsm_non_operational"),
    HW_MODULE_INTERNAL_ERROR("hsm_internal_error"),
    MAINTENANCE_MODE("maintenance_mode"),
    FAILED_TO_SAVE_INSTANCE_IDENTIFIER("failed_to_save_instance_identifier"),
    GLOBAL_CONF_DOWNLOAD_URL_CONNECTION_FAILURE("global_conf_download_url_connection_failure"),
    GLOBAL_CONF_GET_VERSION_FAILED("global_conf_get_version_failed"),
    GLOBAL_CONF_HEADER_FIELD_MISSING("global_conf_header_field_missing"),
    GLOBAL_CONF_HEADER_FIELD_MISSING_PARAMETER("global_conf_header_field_missing_parameter"),
    GLOBAL_CONF_HEADER_FIELD_WRONG_VALUE("global_conf_header_field_wrong_value"),
    GLOBAL_CONF_MISSING_SIGNED_DATA("global_conf_missing_signed_data"),
    GLOBAL_CONF_MISSING_SIGNED_DATA_EXPIRATION_DATE("global_conf_missing_signed_data_expiration_date"),
    GLOBAL_CONF_MISSING_VERIFICATION_CERT("global_conf_missing_verification_cert"),
    GLOBAL_CONF_SIGNATURE_DECODE_FAILURE("global_conf_signature_decode_failure"),
    GLOBAL_CONF_SIGNATURE_VERIFICATION_FAILURE("global_conf_signature_verification_failure"),
    GLOBAL_CONF_PARSING_DOWNLOADED_CONF_DIRECTORY_FAILURE("global_conf_parsing_downloaded_conf_directory_failure"),
    GLOBAL_CONF_PART_INVALID_INSTANCE_IDENTIFIER("global_conf_part_invalid_instance_identifier"),
    GLOBAL_CONF_PART_DOWNLOAD_FAILURE("global_conf_part_download_failure"),
    GLOBAL_CONF_PART_DOWNLOADED_FILE_INTEGRITY_FAILURE("global_conf_part_downloaded_file_integrity_failure"),
    GLOBAL_CONF_PART_DOWNLOADED_HASH_FAILURE("global_conf_part_downloaded_hash_failure"),
    GLOBAL_CONF_PART_FILE_HASH_FAILURE("global_conf_part_file_hash_failure"),
    GLOBAL_CONF_PART_FILE_SAVE_FAILURE("global_conf_part_file_save_failure"),
    GLOBAL_CONF_PART_FILE_EXPIRATION_DATE_UPDATE_FAILURE("global_conf_part_file_expiration_date_update_failure"),

    // ===== SIGNER ERRORS =====
    KEY_NOT_FOUND("key_not_found"),
    KEY_NOT_AVAILABLE("key_not_available"),
    CERT_NOT_FOUND("cert_not_found"),
    CSR_NOT_FOUND("csr_not_found"),
    TOKEN_NOT_FOUND("token_not_found"),
    TOKEN_NOT_INITIALIZED("token_not_initialized"),
    TOKEN_NOT_AVAILABLE("token_not_available"),
    TOKEN_READONLY("token_readonly"),
    CANNOT_SIGN("cannot_sign"),
    UNSUPPORTED_SIGN_ALGORITHM("unsupported_sign_algorithm"),
    CSR_FAILED("failed_to_generate_certificate_request"),
    FAILED_TO_GENERATE_R_KEY("failed_to_generate_private_key"),
    FAILED_TO_GENERATE_U_KEY("failed_to_generate_public_key"),
    FAILED_TO_DELETE_KEY("failed_to_delete_key"),
    CERT_EXISTS("cert_exists"),
    NO_MEMBERID_FROM_CERT("cannot_get_member_id_from_certificate"),
    WRONG_CERT_USAGE("wrong_cert_usage"),
    NO_MEMBERID("cannot_find_member"),
    LOGIN_FAILED("login_failed"),
    LOGOUT_FAILED("logout_failed"),
    CERT_IMPORT_FAILED("cert_import_failed"),
    TOKEN_PIN_POLICY_FAILURE("token_pin_policy_failure"),

    // ===== MESSAGE LOG ERRORS =====
    MLOG_TIMESTAMPER_FAILED("timestamper_failed"),
    TIMESTAMP_REQUEST_TIMED_OUT("mlog.timestamp_request_timed_out"),
    MALFORMED_TIMESTAMP_SERVER_URL("mlog.malformed_timestamp_server_url"),
    TIMESTAMPING_NON_OK_RESPONSE("mlog.timestamping_non_ok_response"),
    MLOG_LOG_MANAGER_UNAVAILABLE("mlog.log_manager_unavailable"),
    NO_TIMESTAMPING_PROVIDER_FOUND("mlog.no_timestamping_provider_found"),
    TIMESTAMP_TOKEN_SIGNER_INFO_NOT_FOUND("mlog.timestamp_token_signer_info_not_found"),
    TSP_CERTIFICATE_NOT_FOUND("mlog.tsp_certificate_not_found"),
    ADDING_SIGNATURE_TO_TS_TOKEN_FAILED("mlog.adding_signature_to_ts_token_failed"),
    TIMESTAMP_TOKEN_ENCODING_FAILED("mlog.timestamp_token_encoding_failed"),
    UPDATING_MESSAGE_SIGNATURE_FAILED("mlog.updating_message_signature_failed"),
    NO_LOG_RECORDS_SPECIFIED("mlog.no_log_records_specified"),
    MESSAGE_LOG_RECORD_NOT_FOUND("mlog.message_log_record_not_found"),
    FAILED_TO_BUILD_SIGNATURE_HASH_CHAIN("mlog.failed_to_build_signature_hash_chain"),
    FAILED_TO_PREPARE_SIGNATURE_DATA("mlog.failed_to_prepare_signature_data"),
    NO_SIGNATURE_HASHES_SPECIFIED("mlog.no_signature_hashes_specified"),
    CALCULATING_MESSAGE_DIGEST_FAILED("mlog.calculating_message_digest_failed"),
    READING_TIMESTAMP_RESPONSE_FAILED("mlog.reading_timestamp_response_failed"),
    TIMESTAMP_PROVIDER_CONNECTION_FAILED("mlog.timestamp_provider_connection_failed"),
    TIMESTAMP_RESPONSE_OBJECT_CREATION_FAILED("mlog.timestamp_response_object_creation_failed"),
    TIMESTAMP_NON_GRANTED_RESPONSE("mlog.timestamp_non_granted_response"),
    TIMESTAMP_RECORD_SAVE_FAILURE("mlog.timestamp_record_save_failure"),
    TIMESTAMP_RESPONSE_VALIDATION_FAILED("mlog.timestamp_response_validation_failed"),
    TIMESTAMP_SIGNER_VERIFICATION_FAILED("mlog.timestamp_signer_verification_failed"),

    // ===== OCSP ERRORS ====='
    OCSP_CONNECTION_ERROR("ocsp_connection_error"),
    OCSP_RESPONSE_PARSING_FAILURE("ocsp_response_parsing_failure"),
    OCSP_FAILED("ocsp_failed"),
    OCSP_RESPONSE_VERIFICATION_FAILURE("ocsp_response_verification_failure"),

    // ===== SECURITY SERVER ERRORS =====
    SECURITY_SERVER_NOT_FOUND("security_server_not_found"),
    SERVICE_NOT_FOUND("service_not_found"),
    CLIENT_NOT_FOUND("client_not_found"),
    ENDPOINT_NOT_FOUND("endpoint_not_found"),
    CERTIFICATE_AUTHORITY_NOT_FOUND("certificate_authority_not_found"),

    // ===== VALIDATION ERRORS =====
    VALIDATION_ERROR("validation_error"),
    INVALID_URL("invalid_url"),
    INVALID_CERTIFICATE("invalid_certificate"),
    INVALID_DISTINGUISHED_NAME("invalid_distinguished_name"),
    INVALID_CHARACTERS("invalid_characters"),
    INVALID_PEM_CSR("invalid_pem_csr"),

    // ===== BUSINESS LOGIC ERRORS =====
    DUPLICATE_ENTRY("duplicate_entry"),
    ACTION_NOT_POSSIBLE("action_not_possible"),
    MANAGEMENT_REQUEST_FAILED("management_request_failed"),
    MANAGEMENT_REQUEST_SENDING_FAILED("management_request_sending_failed"),

    // ===== API KEY ERRORS =====
    API_KEY_NOT_FOUND("api_key_not_found"),
    API_KEY_INVALID_ROLE("invalid_role"),

    // ===== FILE RELATED ERRORS =====
    INVALID_FILENAME("invalid_filename"),
    INVALID_FILE_CONTENT_TYPE("invalid_file_content_type"),
    INVALID_FILE_EXTENSION("invalid_file_extension"),
    DOUBLE_FILE_EXTENSION("double_file_extension"),
    INVALID_BACKUP_FILE("invalid_backup_file"),
    FILE_ALREADY_EXISTS("file_already_exists"),

    // ===== BACKUP ERRORS =====
    BACKUP_FILE_NOT_FOUND("backup_file_not_found"),
    BACKUP_GENERATION_FAILED("backup_generation_failed"),
    BACKUP_GENERATION_INTERRUPTED("generate_backup_interrupted"),
    BACKUP_RESTORATION_FAILED("restore_process_failed"),
    BACKUP_RESTORATION_INTERRUPTED("backup_restore_interrupted"),
    BACKUP_DELETION_FAILED("backup_deletion_failed"),

    // ===== RESOURCE ERRORS =====
    ERROR_RESOURCE_READ("resource_read_failed"),
    ERROR_INVALID_ADDRESS_CHAR("invalid_address_char"),

    // ===== CONFIGURATION VERIFICATION ERRORS =====
    MALFORMED_ANCHOR("malformed_anchor"),
    ANCHOR_NOT_FOR_EXTERNAL_SOURCE("conf_verification.anchor_not_for_external_source"),
    MISSING_PRIVATE_PARAMS("conf_verification.missing_private_params"),
    CONF_VERIFICATION_OTHER("conf_verification.other"),
    CONF_VERIFICATION_OUTDATED("conf_verification.outdated"),
    CONF_VERIFICATION_SIGNATURE("conf_verification.signature_invalid"),
    CONF_VERIFICATION_UNREACHABLE("conf_verification.unreachable"),
    INVALID_DOWNLOAD_URL_FORMAT("conf_download.invalid_download_url_format"),
    CONF_DOWNLOAD_FAILED("conf_download_failed"),
    GLOBAL_CONF_OUTDATED("global_conf_outdated"),

    // ===== OPENAPI ERRORS =====
    ERROR_READING_OPENAPI_FILE("openapi_file_error"),
    INITIALIZATION_INTERRUPTED("initialization_interrupted"),
    EMAIL_SENDING_FAILED("email_sending_error"),

    // ===== TOKEN ERRORS =====
    TOKEN_FETCH_FAILED("token_fetch_failed"),
    TOKEN_PIN_INCORRECT("pin_incorrect"),
    TOKEN_NOT_ACTIVE("token_not_active"),
    TOKEN_WEAK_PIN("token_weak_pin"),

    // ===== CLIENT ERRORS =====
    INVALID_CLIENT_NAME("invalid_client_name"),

    // ===== ANCHOR ERRORS =====
    ANCHOR_FILE_NOT_FOUND("anchor_file_not_found"),

    // ===== TIMESTAMPING ERRORS =====
    TIMESTAMPING_SERVICE_NOT_FOUND("timestamping_service_not_found"),

    // ===== GPG ERRORS =====
    GPG_KEY_GENERATION_FAILED("gpg_key_generation_failed"),
    GPG_KEY_GENERATION_INTERRUPTED("gpg_key_generation_interrupted"),

    // ===== INTERNAL_CERT ERRORS =====
    IMPORT_INTERNAL_CERT_FAILED("import_internal_cert_failed"),
    INTERNAL_KEY_CERT_INTERRUPTED("internal_key_cert_interrupted"),

    // ===== CERTIFICATE ERRORS =====
    KEY_CERT_GENERATION_FAILED("key_and_cert_generation_failed"),

    // ===== MEMBER ERRORS =====
    MEMBER_CLASS_EXISTS("member_class_exists"),

    MISSING_SECRET("missing_secret"),

    PGP_INTERNAL_ERROR("pgp_internal_error"),
    PGP_ENCODE_FAILED("pgp_encode_failed"),
    PGP_ENCRYPTION_KEYS_MISSING("pgp_encryption_keys_missing"),

    TOKEN_PIN_MISSING("token_pin_missing");

    private final String code;

    @Override
    public String code() {
        return code;
    }

    /**
     * Get ErrorDeviation from string code.
     *
     * @param code the string code
     * @return the ErrorDeviation built from the code
     */
    public static DeviationBuilder.ErrorDeviationBuilder withCode(String code) {
        return () -> prepareCode(code);
    }

    private static String prepareCode(String code) {
        if (code == null) {
            log.warn("Error code is null, defaulting to {}", INTERNAL_ERROR.code());
            return INTERNAL_ERROR.code();
        }
        return code.toLowerCase();
    }

    public static ErrorCode fromCode(String code) {
        for (ErrorCode errorCode : values()) {
            if (errorCode.code.equals(code)) {
                return errorCode;
            }
        }
        log.warn("Unknown error code '{}'", code);
        return null;
    }
}
