/*
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
package org.niis.xroad.common.exception.util;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.restapi.exceptions.DeviationBuilder;

import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_METADATA_MEMBER_CLASS_EXISTS;

@RequiredArgsConstructor
public enum CommonDeviationMessage implements DeviationBuilder.ErrorDeviationBuilder {
    INTERNAL_ERROR("internal_error"),
    ACTION_NOT_POSSIBLE("action_not_possible"),
    GENERIC_VALIDATION_FAILURE("invalid_parameters"),
    SECURITY_SERVER_NOT_FOUND("security_server_not_found"),
    INVALID_ENCODED_ID("invalid_encoded_id"),
    ERROR_ID_NOT_A_NUMBER("id_not_a_number"),

    API_KEY_NOT_FOUND("api_key_not_found"),
    USER_NOT_FOUND("user_not_found"),
    INVALID_ROLE("invalid_role"),
    INVALID_FILENAME("invalid_filename"),
    INVALID_FILE_CONTENT_TYPE("invalid_file_content_type"),
    INVALID_FILE_EXTENSION("invalid_file_extension"),
    DOUBLE_FILE_EXTENSION("double_file_extension"),
    INVALID_BACKUP_FILE("invalid_backup_file"),

    BACKUP_FILE_NOT_FOUND("backup_file_not_found"),
    BACKUP_GENERATION_FAILED("backup_generation_failed"),
    BACKUP_GENERATION_INTERRUPTED("generate_backup_interrupted"),
    BACKUP_RESTORATION_FAILED("restore_process_failed"),
    BACKUP_RESTORATION_INTERRUPTED("backup_restore_interrupted"),
    BACKUP_DELETION_FAILED("backup_deletion_failed"),

    ERROR_RESOURCE_READ("resource_read_failed"),
    ERROR_INVALID_ADDRESS_CHAR("invalid_address_char"),
    INVALID_URL("invalid_url"),

    MALFORMED_ANCHOR("malformed_anchor"),
    ANCHOR_NOT_FOR_EXTERNAL_SOURCE("conf_verification.anchor_not_for_external_source"),
    MISSING_PRIVATE_PARAMS("conf_verification.missing_private_params"),
    CONF_VERIFICATION_OTHER("conf_verification.other"),
    CONF_VERIFICATION_OUTDATED("conf_verification.outdated"),
    CONF_VERIFICATION_SIGNATURE("conf_verification.signature_invalid"),
    CONF_VERIFICATION_UNREACHABLE("conf_verification.unreachable"),
    INVALID_DOWNLOAD_URL_FORMAT("conf_download.invalid_download_url_format"),
    CONF_DOWNLOAD_FAILED("conf_download_failed"),
    OUTDATED_GLOBAL_CONF("global_conf_outdated"),


    ERROR_READING_OPENAPI_FILE("openapi_file_error"),
    INITIALIZATION_INTERRUPTED("initialization_interrupted"),
    EMAIL_SENDING_FAILED("email_sending_error"),

    TOKEN_FETCH_FAILED("token_fetch_failed"),
    TOKEN_PIN_INCORRECT("pin_incorrect"),
    TOKEN_NOT_ACTIVE("token_not_active"),
    TOKEN_NOT_FOUND("token_not_found"),
    TOKEN_WEAK_PIN("token_weak_pin"),

    USER_WEAK_PASSWORD("user_weak_password"),
    USER_PASSWORD_INVALID_CHARACTERS("user_password_invalid_characters"),

    CLIENT_NOT_FOUND("client_not_found"),
    INVALID_CLIENT_NAME("invalid_client_name"),

    ANCHOR_FILE_NOT_FOUND("anchor_file_not_found"),

    INVALID_DISTINGUISHED_NAME("invalid_distinguished_name"),

    MANAGEMENT_REQUEST_SENDING_FAILED("management_request_sending_failed"),

    TIMESTAMPING_SERVICE_NOT_FOUND("timestamping_service_not_found"),

    GPG_KEY_GENERATION_FAILED("gpg_key_generation_failed"),

    KEY_CERT_GENERATION_FAILED("key_and_cert_generation_failed"),
    KEY_NOT_FOUND("key_not_found"),
    CERTIFICATE_ALREADY_EXISTS("certificate_already_exists"),
    INVALID_CERTIFICATE("invalid_certificate"),

    MEMBER_CLASS_EXISTS(ERROR_METADATA_MEMBER_CLASS_EXISTS),

    PASSWORD_INCORRECT("password_incorrect");

    private final String code;


    @Override
    public String code() {
        return code;
    }


}
