/*
 * The MIT License
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
package org.niis.xroad.securityserver.restapi.exceptions;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.core.exception.DeviationBuilder;

import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_ACCESSRIGHT_NOT_FOUND;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_ADDITIONAL_MEMBER_ALREADY_EXISTS;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_ANCHOR_EXISTS;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_ANCHOR_NOT_FOUND;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_ANCHOR_UPLOAD_FAILED;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_AUTH_CERT_NOT_SUPPORTED;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_BASE_ENDPOINT_NOT_FOUND;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_CANNOT_DELETE_OWNER;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_CANNOT_MAKE_OWNER;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_CANNOT_REGISTER_OWNER;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_CANNOT_UNREGISTER_OWNER;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_CA_CERT_PROCESSING;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_CA_NOT_FOUND;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_CERTIFICATE_NOT_FOUND;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_CERTIFICATE_NOT_FOUND_WITH_ID;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_CERTIFICATE_WRONG_USAGE;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_CLIENT_ALREADY_EXISTS;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_CSR_NOT_FOUND;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_DIAGNOSTIC_REQUEST_FAILED;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_DUPLICATE_ACCESSRIGHT;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_DUPLICATE_CONFIGURED_TIMESTAMPING_SERVICE;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_DUPLICATE_LOCAL_GROUP_CODE;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_ENDPOINT_NOT_FOUND;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_EXISTING_ENDPOINT;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_EXISTING_SERVICE_CODE;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_EXISTING_URL;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_GPG_KEY_GENERATION_INTERRUPTED;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_ILLEGAL_GENERATED_ENDPOINT_REMOVE;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_ILLEGAL_GENERATED_ENDPOINT_UPDATE;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_INSTANTIATION_FAILED;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_INTERNAL_ANCHOR_UPLOAD_INVALID_INSTANCE_ID;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_INVALID_CHARACTERS_PIN;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_INVALID_CONNECTION_TYPE;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_INVALID_DN_PARAMETER;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_INVALID_HTTPS_URL;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_INVALID_INIT_PARAMS;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_INVALID_INSTANCE_IDENTIFIER;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_INVALID_MEMBER_CLASS;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_INVALID_SERVICE_URL;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_LOCAL_GROUP_MEMBER_ALREADY_EXISTS;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_LOCAL_GROUP_MEMBER_NOT_FOUND;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_LOCAL_GROUP_NOT_FOUND;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_MALFORMED_URL;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_MISSING_PARAMETER;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_OPENAPI_FILE_NOT_FOUND;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_OPENAPI_PARSING;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_ORPHANS_NOT_FOUND;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_SERVER_ALREADY_FULLY_INITIALIZED;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_SERVICE_CLIENT_NOT_FOUND;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_SERVICE_EXISTS;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_SERVICE_NOT_FOUND;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_SIGN_CERT_NOT_SUPPORTED;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_SOFTWARE_TOKEN_INIT_FAILED;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_UNSUPPORTED_OPENAPI_VERSION;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_WRONG_KEY_USAGE;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_WRONG_TYPE;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_WSDL_DOWNLOAD_FAILED;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_WSDL_EXISTS;


@RequiredArgsConstructor
public enum ErrorMessage implements DeviationBuilder.ErrorDeviationBuilder {
    FAILED_COLLECT_SYSTEM_INFORMATION("failed_collect_system_information"),
    CERTIFICATE_WRONG_USAGE(ERROR_CERTIFICATE_WRONG_USAGE),
    AUTH_CERT_NOT_SUPPORTED(ERROR_AUTH_CERT_NOT_SUPPORTED),
    SIGN_CERT_NOT_SUPPORTED(ERROR_SIGN_CERT_NOT_SUPPORTED),
    CERTIFICATE_NOT_FOUND(ERROR_CERTIFICATE_NOT_FOUND),
    CERTIFICATE_NOT_FOUND_WITH_ID(ERROR_CERTIFICATE_NOT_FOUND_WITH_ID),
    CA_NOT_FOUND(ERROR_CA_NOT_FOUND),
    CA_CERT_PROCESSING(ERROR_CA_CERT_PROCESSING),
    CP_INSTANTIATION_FAILED(ERROR_INSTANTIATION_FAILED),
    CSR_NOT_FOUND(ERROR_CSR_NOT_FOUND),
    INVALID_DN_PARAMETER(ERROR_INVALID_DN_PARAMETER),
    MEMBER_ID_REQUIRED_FOR_SIGN_CSR("memberId_required_for_sign_csr"),
    GPG_KEY_GENERATION_INTERRUPTED(ERROR_GPG_KEY_GENERATION_INTERRUPTED),

    DUPLICATE_CONFIGURED_TIMESTAMPING_SERVICE(ERROR_DUPLICATE_CONFIGURED_TIMESTAMPING_SERVICE),

    ANCHOR_EXISTS(ERROR_ANCHOR_EXISTS),
    ANCHOR_UPLOAD_FAILED(ERROR_ANCHOR_UPLOAD_FAILED),
    ANCHOR_NOT_FOUND(ERROR_ANCHOR_NOT_FOUND),

    INTERNAL_ANCHOR_UPLOAD_INVALID_INSTANCE_ID(ERROR_INTERNAL_ANCHOR_UPLOAD_INVALID_INSTANCE_ID),

    FORBIDDEN_DISABLE_MANAGEMENT_SERVICE_CLIENT("forbidden_disable_management_service_client"),
    FORBIDDEN_ENABLE_MAINTENANCE_MODE_FOR_MANAGEMENT_SERVICE("forbidden_enable_maintenance_mode_for_management_service"),
    DUPLICATE_MAINTENANCE_MODE_CHANGE_REQUEST("maintenance_mode_change_request_already_submitted"),
    ALREADY_ENABLED_MAINTENANCE_MODE("already_enabled_maintenance_mode"),
    ALREADY_DISABLED_MAINTENANCE_MODE("already_disabled_maintenance_mode"),
    DUPLICATE_ADDRESS_CHANGE_REQUEST("address_change_request_already_submitted"),
    SAME_ADDRESS_CHANGE_REQUEST("same_address_change_request"),
    CANNOT_REGISTER_OWNER(ERROR_CANNOT_REGISTER_OWNER),
    CANNOT_UNREGISTER_OWNER(ERROR_CANNOT_UNREGISTER_OWNER),
    CANNOT_MAKE_OWNER(ERROR_CANNOT_MAKE_OWNER),
    CLIENT_ALREADY_EXISTS(ERROR_CLIENT_ALREADY_EXISTS),
    ADDITIONAL_MEMBER_ALREADY_EXISTS(ERROR_ADDITIONAL_MEMBER_ALREADY_EXISTS),
    CANNOT_DELETE_OWNER(ERROR_CANNOT_DELETE_OWNER),
    INVALID_CLIENT_TYPE("invalid_client_type"),
    CLIENT_RENAME_ALREADY_SUBMITTED("client_rename_already_submitted"),
    CLIENT_NOT_FOUND_BY_LOCAL_GROUP_ID("client_not_found_by_local_group_id"),

    OPENAPI_FILE_NOT_FOUND(ERROR_OPENAPI_FILE_NOT_FOUND),
    OPENAPI_PARSING(ERROR_OPENAPI_PARSING),
    UNSUPPORTED_OPENAPI_VERSION(ERROR_UNSUPPORTED_OPENAPI_VERSION),
    EXISTING_URL(ERROR_EXISTING_URL),

    WSDL_DOWNLOAD_FAILED(ERROR_WSDL_DOWNLOAD_FAILED),
    WSDL_EXISTS(ERROR_WSDL_EXISTS),
    WRONG_KEY_USAGE(ERROR_WRONG_KEY_USAGE),

    MEMBER_NAME_NOT_FOUND("member_name_not_found"),
    INVALID_MEMBER_CLASS(ERROR_INVALID_MEMBER_CLASS),
    INVALID_INSTANCE_IDENTIFIER(ERROR_INVALID_INSTANCE_IDENTIFIER),
    INSTANCE_IDENTIFIER_NOT_FOUND("instance_identifier_not_found"),

    INVALID_CONNECTION_TYPE(ERROR_INVALID_CONNECTION_TYPE),
    CONNECTION_TYPE_REQUIRED("connection_type_required"),

    ACCESS_RIGHT_NOT_FOUND(ERROR_ACCESSRIGHT_NOT_FOUND),
    DUPLICATE_ACCESS_RIGHT(ERROR_DUPLICATE_ACCESSRIGHT),

    SERVICE_NOT_FOUND(ERROR_SERVICE_NOT_FOUND),
    SERVICE_CLIENT_NOT_FOUND(ERROR_SERVICE_CLIENT_NOT_FOUND),
    INVALID_SERVICE_CLIENT_ID("invalid_service_client_id"),
    INVALID_SERVICE_URL(ERROR_INVALID_SERVICE_URL),
    SERVICE_EXISTS(ERROR_SERVICE_EXISTS),
    EXISTING_SERVICE_CODE(ERROR_EXISTING_SERVICE_CODE),
    WRONG_SERVICE_DESCRIPTION_TYPE(ERROR_WRONG_TYPE),
    UNKNOWN_SERVICE_DESCRIPTION_TYPE("unknown_service_description_type"),
    SERVICE_DESCRIPTION_NOT_FOUND("service_description_not_found"),

    ENDPOINT_NOT_FOUND(ERROR_ENDPOINT_NOT_FOUND),
    BASE_ENDPOINT_NOT_FOUND(ERROR_BASE_ENDPOINT_NOT_FOUND),
    EXISTING_ENDPOINT(ERROR_EXISTING_ENDPOINT),
    ILLEGAL_GENERATED_ENDPOINT_REMOVE(ERROR_ILLEGAL_GENERATED_ENDPOINT_REMOVE),
    ILLEGAL_GENERATED_ENDPOINT_UPDATE(ERROR_ILLEGAL_GENERATED_ENDPOINT_UPDATE),
    ENDPOINT_ID_SHOULD_NOT_BE_PROVIDED("endpoint_id_should_not_be_provided"),

    DIAGNOSTIC_REQUEST_FAILED(ERROR_DIAGNOSTIC_REQUEST_FAILED),

    MALFORMED_URL(ERROR_MALFORMED_URL),
    INVALID_HTTPS_URL(ERROR_INVALID_HTTPS_URL),
    INVALID_CHARACTERS_PIN(ERROR_INVALID_CHARACTERS_PIN),
    INVALID_INIT_PARAMS(ERROR_INVALID_INIT_PARAMS),
    MISSING_PARAMETER(ERROR_MISSING_PARAMETER),
    SERVER_ALREADY_FULLY_INITIALIZED(ERROR_SERVER_ALREADY_FULLY_INITIALIZED),

    SOFTWARE_TOKEN_INIT_FAILED(ERROR_SOFTWARE_TOKEN_INIT_FAILED),
    MISSING_TOKEN_PASSWORD("missing_token_password"),

    LOCAL_GROUP_NOT_FOUND(ERROR_LOCAL_GROUP_NOT_FOUND),
    LOCAL_GROUP_MEMBER_ALREADY_EXISTS(ERROR_LOCAL_GROUP_MEMBER_ALREADY_EXISTS),
    LOCAL_GROUP_MEMBER_NOT_FOUND(ERROR_LOCAL_GROUP_MEMBER_NOT_FOUND),
    DUPLICATE_LOCAL_GROUP_CODE(ERROR_DUPLICATE_LOCAL_GROUP_CODE),
    MISSING_MEMBER_ID("missing_member_id"),

    ORPHANS_NOT_FOUND(ERROR_ORPHANS_NOT_FOUND);

    private final String code;

    @Override
    public String code() {
        return code;
    }


}
