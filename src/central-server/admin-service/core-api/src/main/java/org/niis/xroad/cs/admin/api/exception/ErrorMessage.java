/**
 * The MIT License
 * <p>
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.cs.admin.api.exception;

import lombok.Getter;
import org.niis.xroad.restapi.exceptions.DeviationProvider;

public enum ErrorMessage implements DeviationProvider {
    MEMBER_CLASS_IS_IN_USE("member_class_is_in_use", "Cannot delete member class: Found X-Road members belonging to the class."
            + " Only classes with no registered members can be deleted."),
    MEMBER_CLASS_NOT_FOUND("member_class_not_found", "No member class with the specified code found."),
    MEMBER_CLASS_EXISTS("member_class_exists", "Member class with the same code already exists."),
    MEMBER_NOT_FOUND("member_not_found", "No member with the specified code/id found."),
    MEMBER_EXISTS("member_exists", "Member with the same code already exists."),

    SUBSYSTEM_EXISTS("subsystem_exists", "Subsystem with the same code already exists."),
    SUBSYSTEM_NOT_FOUND("subsystem_not_found", "Subsystem with specified code not found."),
    SUBSYSTEM_REGISTERED_AND_CANNOT_BE_DELETED("subsystem_registered_and_cannot_be_deleted", "Cannot delete already registered subsystem."),
    SUBSYSTEM_NOT_REGISTERED_TO_SECURITY_SERVER("subsystem_not_registered_to_security_server",
            "Subsystem is not registered to the given security server."),
    SUBSYSTEM_ALREADY_REGISTERED_TO_SECURITY_SERVER("subsystem_already_registered_to_security_server",
            "Subsystem is already registered to the security server."),

    SS_AUTH_CERTIFICATE_NOT_FOUND("ss_auth_certificate_not_found", "Authentication certificate not found"),
    MANAGEMENT_SERVICE_PROVIDER_NOT_SET("management_service_provider_not_set", "Management service provider not set"),
    MR_NOT_FOUND("management_request_not_found", "No management request with the specified id found."),
    MR_EXISTS("management_request_exists", "A pending management request already exists."),
    MR_SECURITY_SERVER_EXISTS("management_request_security_server_exists", "Certificate is already registered."),
    MR_INVALID_AUTH_CERTIFICATE("management_request_invalid_auth_certificate", "Invalid authentication certificate"),
    MR_INVALID_STATE_FOR_APPROVAL("management_request_invalid_state_for_approval", "Management request can not be approved"),
    MR_SERVER_OWNER_NOT_FOUND("management_request_server_owner_not_found", "Security server owner not found"),
    MR_INVALID_STATE("management_request_invalid_state", "Requested operation can not be applied in this state"),
    MR_NOT_SUPPORTED("management_request_not_supported", "Unknown management request type"),
    MR_CANNOT_REGISTER_OWNER("management_request_cannot_register_owner", "Cannot register owner as a client"),
    MR_MEMBER_NOT_FOUND("management_request_member_not_found", "Member does not exist"),
    MR_SERVER_NOT_FOUND("management_request_server_not_found", "Security server not found"),
    MR_CLIENT_REGISTRATION_NOT_FOUND("management_request_client_registration_not_found", "Client registration does not exist"),
    MR_CLIENT_ALREADY_REGISTERED("management_request_client_already_registered", "Client already registered to a server"),
    MR_OWNER_MUST_BE_MEMBER("management_request_owner_must_be_member", "Owner must be a member"),
    MR_OWNER_MUST_BE_CLIENT("management_request_owner_must_be_client", "Owner is not registered as client on the security server"),
    MR_CLIENT_ALREADY_OWNER("management_request_client_already_owner", "Client is already owner of the security server"),
    MR_SERVER_CODE_EXISTS("management_request_server_code_exists", "Member already owns a security server with server code"),
    MR_UNKNOWN_TYPE("management_request_unknown_type", "Unknown request type"),

    INVALID_SERVICE_PROVIDER_ID("invalid_service_provider_id", "Invalid service provider id"),
    INVALID_MEMBER_ID("invalid_member_id", "Invalid member id"),
    INVALID_SUBSYSTEM_ID("invalid_subsystem_id", "Invalid subsystem id"),
    GLOBAL_GROUP_NOT_FOUND("global_group_not_found", "Global group by given code does not exist"),
    OWNERS_GLOBAL_GROUP_CANNOT_BE_DELETED("owners_global_group_cannot_be_deleted", "Cannot perform delete action on server owners group"),
    OWNERS_GLOBAL_GROUP_MEMBER_CANNOT_BE_DELETED("owners_global_group_member_cannot_be_deleted",
            "Cannot perform delete action on server owners group member"),
    CANNOT_ADD_MEMBER_TO_OWNERS_GROUP("cannot_add_member_to_owners_group",
            "Cannot perform add member action on server owners group"),
    GLOBAL_GROUP_EXISTS("global_group_exists", "Global group with the same code already exists."),
    SECURITY_SERVER_NOT_FOUND("security_server_not_found", "Security server not found"),
    CERTIFICATION_SERVICE_NOT_FOUND("certification_service_not_found", "Certification service not found."),
    INVALID_CERTIFICATE("invalid_certificate", "Invalid X.509 certificate"),
    INTERMEDIATE_CA_NOT_FOUND("intermediate_ca_not_found", "Intermediate CA not found"),
    TIMESTAMPING_AUTHORITY_NOT_FOUND("timestamping_authority_not_found", "Timestamping authority not found"),
    OCSP_RESPONDER_NOT_FOUND("ocsp_responder_not_found", "OCSP Responder not found"),
    INVALID_URL("invalid_url", "Invalid url"),
    CONFIGURATION_NOT_FOUND("configuration_not_found", "Configuration Source not found"),
    CONFIGURATION_PART_FILE_NOT_FOUND("configuration_part_file_not_found", "Configuration part file not found"),
    CONFIGURATION_PART_VALIDATOR_NOT_FOUND("configuration_part_validator_not_found", "Configuration part validator not found"),
    UNKNOWN_CONFIGURATION_PART("unknown_configuration_part", "Unknown configuration part"),
    CONFIGURATION_PART_VALIDATION_FAILED("configuration_part_validation_failed", "Configuration part validation failed"),


    SIGNER_PROXY_ERROR("signer_proxy_error", "Signer proxy exception"),

    SIGNING_KEY_ACTION_NOT_POSSIBLE("signing_key_action_not_possible", "Signing key action not possible"),
    KEY_GENERATION_FAILED("key_generation_failed", "Signing key generation failed"),
    SIGNING_KEY_NOT_FOUND("signing_key_not_found", "Signing key not found"),
    ERROR_DELETING_SIGNING_KEY("error_deleting_signing_key", "Error deleting signing key"),
    ERROR_ACTIVATING_SIGNING_KEY("error_activating_signing_key", "Error activating signing key"),

    NO_CONFIGURATION_SIGNING_KEYS_CONFIGURED("no_configuration_signing_keys_configured", "No configuration signing keys configured"),
    INSTANCE_IDENTIFIER_NOT_SET("instance_identifier_not_set", "System parameter for instance identifier not set"),
    ERROR_RECREATING_ANCHOR("error_recreating_anchor", "Error re-creating anchor file"),
    CERTIFICATE_PROFILE_INFO_CLASS_NOT_FOUND("certificate_profile_info_class_not_found", "Certificate profile info class was not found"),

    INIT_SOFTWARE_TOKEN_FAILED("init_software_token_failed", "Software token initialization failed"),
    INIT_INVALID_PARAMS("init_invalid_params", "Empty, missing or redundant parameters provided for initialization"),
    INIT_ALREADY_INITIALIZED("init_already_initialized", "Central server Initialization failed, already fully initialized"),
    INIT_SIGNER_PIN_POLICY_FAILED("init_signer_pin_policy_failed", "Token pin policy failure at Signer"),

    TOKEN_INVALID_CHARACTERS("token_invalid_characters", "The provided pin code contains invalid characters"),
    TOKEN_WEAK_PIN("token_weak_pin", "The provided pin code was too weak"),
    TOKEN_NOT_FOUND("token_not_found", "Token not found"),
    TOKEN_ACTIVATION_FAILED("token_activation_failed", "Token activation failed"),
    TOKEN_DEACTIVATION_FAILED("token_deactivation_failed", "Token deactivation failed"),
    TOKEN_PIN_LOCKED("token_pin_locked", "Token PIN locked"),
    TOKEN_PIN_FINAL_TRY("token_pin_final_try", "Tries left: 1"),
    TOKEN_INCORRECT_PIN_FORMAT("token_incorrect_pin_format", "Incorrect PIN format"),
    TOKEN_ACTION_NOT_POSSIBLE("token_action_not_possible", "Token action not possible"),
    TOKEN_FETCH_FAILED("token_fetch_failed", "Error getting tokens"),

    MALFORMED_ANCHOR("malformed_anchor", "Malformed anchor file"),
    TRUSTED_ANCHOR_VERIFICATION_FAILED("trusted_anchor_verification_failed", "Trusted anchor file verification failed"),
    TRUSTED_ANCHOR_NOT_FOUND("trusted_anchor_not_found", "Trusted anchor not found"),

    INVALID_PAGINATION_PROPERTIES("invalid_pagination_properties", "Pagination has invalid properties"),
    INVALID_SORTING_PROPERTIES("invalid_sort_properties", "Invalid sort parameter");


    @Getter
    private final String code;

    @Getter
    private final String description;

    ErrorMessage(final String code, final String description) {
        this.code = code;
        this.description = description;
    }

}
