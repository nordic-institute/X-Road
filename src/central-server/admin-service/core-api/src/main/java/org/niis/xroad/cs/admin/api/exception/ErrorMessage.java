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
package org.niis.xroad.cs.admin.api.exception;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.restapi.exceptions.DeviationBuilder;

@RequiredArgsConstructor
public enum ErrorMessage implements DeviationBuilder.ErrorDeviationBuilder {
    MEMBER_CLASS_IS_IN_USE("member_class_is_in_use"),
    MEMBER_CLASS_NOT_FOUND("member_class_not_found"),
    MEMBER_NOT_FOUND("member_not_found"),
    MEMBER_EXISTS("member_exists"),
    MEMBER_NOT_REGISTERED_TO_SECURITY_SERVER("member_not_registered_to_security_server"),

    SUBSYSTEM_EXISTS("subsystem_exists"),
    SUBSYSTEM_NOT_FOUND("subsystem_not_found"),
    SUBSYSTEM_REGISTERED_AND_CANNOT_BE_DELETED("subsystem_registered_and_cannot_be_deleted"),
    SUBSYSTEM_NOT_REGISTERED_TO_SECURITY_SERVER("subsystem_not_registered_to_security_server"),
    SUBSYSTEM_ALREADY_REGISTERED_TO_SECURITY_SERVER("subsystem_already_registered_to_security_server"),

    SS_AUTH_CERTIFICATE_NOT_FOUND("ss_auth_certificate_not_found"),
    MANAGEMENT_SERVICE_PROVIDER_NOT_SET("management_service_provider_not_set"),
    MR_NOT_FOUND("management_request_not_found"),
    MR_EXISTS("management_request_exists"),
    MR_SECURITY_SERVER_EXISTS("management_request_security_server_exists"),
    MR_INVALID_AUTH_CERTIFICATE("management_request_invalid_auth_certificate"),
    MR_INVALID_STATE_FOR_APPROVAL("management_request_invalid_state_for_approval"),
    MR_SERVER_OWNER_NOT_FOUND("management_request_server_owner_not_found"),
    MR_INVALID_STATE("management_request_invalid_state"),
    MR_NOT_SUPPORTED("management_request_not_supported"),
    MR_CANNOT_REGISTER_OWNER("management_request_cannot_register_owner"),
    MR_MEMBER_NOT_FOUND("management_request_member_not_found"),
    MR_SERVER_NOT_FOUND("management_request_server_not_found"),
    MR_CLIENT_REGISTRATION_NOT_FOUND("management_request_client_registration_not_found"),
    MR_CLIENT_ALREADY_REGISTERED("management_request_client_already_registered"),
    MR_OWNER_MUST_BE_MEMBER("management_request_owner_must_be_member"),
    MR_OWNER_MUST_BE_CLIENT("management_request_owner_must_be_client"),
    MR_CLIENT_ALREADY_OWNER("management_request_client_already_owner"),
    MR_SERVER_CODE_EXISTS("management_request_server_code_exists"),
    MR_INVALID_SERVER_ADDRESS("management_request_invalid_server_address"),
    MR_INVALID_SUBSYSTEM_NAME("management_request_invalid_subsystem_name"),
    MR_ONLY_SUBSYSTEM_RENAME_ALLOWED("management_request_only_subsystem_rename_allowed"),
    MR_SERVER_CLIENT_NOT_FOUND("management_request_server_client_not_found"),
    MR_UNKNOWN_TYPE("management_request_unknown_type"),

    INVALID_SERVICE_PROVIDER_ID("invalid_service_provider_id"),
    INVALID_MEMBER_ID("invalid_member_id"),
    INVALID_SUBSYSTEM_ID("invalid_subsystem_id"),
    GLOBAL_GROUP_NOT_FOUND("global_group_not_found"),
    OWNERS_GLOBAL_GROUP_CANNOT_BE_DELETED("owners_global_group_cannot_be_deleted"),
    OWNERS_GLOBAL_GROUP_MEMBER_CANNOT_BE_DELETED("owners_global_group_member_cannot_be_deleted"),
    CANNOT_ADD_MEMBER_TO_OWNERS_GROUP("cannot_add_member_to_owners_group"),
    GLOBAL_GROUP_EXISTS("global_group_exists"),
    SECURITY_SERVER_NOT_FOUND("security_server_not_found"),
    CERTIFICATION_SERVICE_NOT_FOUND("certification_service_not_found"),
    INTERMEDIATE_CA_NOT_FOUND("intermediate_ca_not_found"),
    TIMESTAMPING_AUTHORITY_NOT_FOUND("timestamping_authority_not_found"),
    OCSP_RESPONDER_NOT_FOUND("ocsp_responder_not_found"),
    INVALID_URL("invalid_url"),
    INVALID_IP_ADDRESS("invalid_ip_address"),
    CONFIGURATION_NOT_FOUND("configuration_not_found"),
    CONFIGURATION_PART_FILE_NOT_FOUND("configuration_part_file_not_found"),
    CONFIGURATION_PART_VALIDATOR_NOT_FOUND("configuration_part_validator_not_found"),
    UNKNOWN_CONFIGURATION_PART("unknown_configuration_part"),
    CONFIGURATION_PART_VALIDATION_FAILED("configuration_part_validation_failed"),
    KEY_CERT_GENERATION_INTERRUPTED("generate_key_cert_interrupted"),
    CSR_GENERATION_FAILED("csr_generation_failed"),
    BYTES_TO_CERTIFICATE_FAILED("cannot_convert_bytes_to_certificate"),
    CERTIFICATE_WRITING_FAILED("certificate_writing_failed"),
    CERTIFICATE_READ_FAILED("cannot_read_certificate"),
    IMPORTED_KEY_NOT_FOUND("key_not_found"),
    IMPORTED_CERTIFICATE_ALREADY_EXISTS("certificate_already_exists"),
    CERTIFICATE_IMPORT_FAILED("certificate_import_failed"),

    SIGNER_PROXY_ERROR("signer_proxy_error"),

    SIGNING_KEY_ACTION_NOT_POSSIBLE("signing_key_action_not_possible"),
    KEY_GENERATION_FAILED("key_generation_failed"),
    SIGNING_KEY_NOT_FOUND("signing_key_not_found"),
    ERROR_DELETING_SIGNING_KEY("error_deleting_signing_key"),
    ERROR_ACTIVATING_SIGNING_KEY("error_activating_signing_key"),

    NO_CONFIGURATION_SIGNING_KEYS_CONFIGURED("no_configuration_signing_keys_configured"),
    INSTANCE_IDENTIFIER_NOT_SET("instance_identifier_not_set"),
    ERROR_RECREATING_ANCHOR("error_recreating_anchor"),
    CERTIFICATE_PROFILE_INFO_CLASS_NOT_FOUND("certificate_profile_info_class_not_found"),

    INIT_SOFTWARE_TOKEN_FAILED("init_software_token_failed"),
    INIT_INVALID_PARAMS("init_invalid_params"),
    INIT_ALREADY_INITIALIZED("init_already_initialized"),
    INIT_SIGNER_PIN_POLICY_FAILED("init_signer_pin_policy_failed"),

    TOKEN_INVALID_CHARACTERS("token_invalid_characters"),
    TOKEN_ACTIVATION_FAILED("token_activation_failed"),
    TOKEN_DEACTIVATION_FAILED("token_deactivation_failed"),
    TOKEN_PIN_LOCKED("token_pin_locked"),
    TOKEN_PIN_FINAL_TRY("token_pin_final_try"),
    TOKEN_INCORRECT_PIN_FORMAT("token_incorrect_pin_format"),
    TOKEN_ACTION_NOT_POSSIBLE("token_action_not_possible"),

    TRUSTED_ANCHOR_VERIFICATION_FAILED("trusted_anchor_verification_failed"),
    TRUSTED_ANCHOR_NOT_FOUND("trusted_anchor_not_found"),

    INVALID_PAGINATION_PROPERTIES("invalid_pagination_properties"),
    INVALID_SORTING_PROPERTIES("invalid_sort_properties");


    private final String code;

    @Override
    public String code() {
        return code;
    }

}
