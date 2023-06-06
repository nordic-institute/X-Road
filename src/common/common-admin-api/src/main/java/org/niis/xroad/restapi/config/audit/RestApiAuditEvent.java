/**
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
package org.niis.xroad.restapi.config.audit;

/**
 * Known audit events for this module
 */
public enum RestApiAuditEvent {

    FORM_LOGIN("Log in user"),
    FORM_LOGOUT("Log out user"),

    KEY_MANAGEMENT_PAM_LOGIN("Key management API log in"),
    API_KEY_AUTHENTICATION("API key authentication"),
    AUTH_CREDENTIALS_DISCOVERY("Auth credentials discovery"),

    API_KEY_CREATE("API key create"),
    API_KEY_UPDATE("API key update"),
    API_KEY_REMOVE("API key remove"),

    INIT_ANCHOR("Initialize anchor"),
    INIT_SERVER_CONFIGURATION("Initialize server configuration"),
    INIT_CENTRAL_SERVER("Initialize central server"),

    // clients events
    ADD_CLIENT("Add client"),
    REGISTER_CLIENT("Register client"),
    UNREGISTER_CLIENT("Unregister client"),
    DELETE_CLIENT("Delete client"),
    DELETE_ORPHANS("Delete orphaned client keys, certs and certificates"),
    SEND_OWNER_CHANGE_REQ("Change owner"),
    ADD_SERVICE_DESCRIPTION("Add service description"),
    DELETE_SERVICE_DESCRIPTION("Delete service description"),
    DISABLE_SERVICE_DESCRIPTION("Disable service description"),
    ENABLE_SERVICE_DESCRIPTION("Enable service description"),
    REFRESH_SERVICE_DESCRIPTION("Refresh service description"),
    EDIT_SERVICE_DESCRIPTION("Edit service description"),
    EDIT_SERVICE_PARAMS("Edit service parameters"),
    ADD_REST_ENDPOINT("Add rest endpoint"),
    EDIT_REST_ENDPOINT("Edit rest endpoint"),
    DELETE_REST_ENDPOINT("Delete rest endpoint"),
    ADD_SERVICE_ACCESS_RIGHTS("Add access rights to service"),
    REMOVE_SERVICE_ACCESS_RIGHTS("Remove access rights from service"),
    ADD_SERVICE_CLIENT_ACCESS_RIGHTS("Add access rights to subject"),
    REMOVE_SERVICE_CLIENT_ACCESS_RIGHTS("Remove access rights from subject"),
    SET_CONNECTION_TYPE("Set connection type for servers in service consumer role"),
    ADD_CLIENT_INTERNAL_CERT("Add internal TLS certificate"),
    DELETE_CLIENT_INTERNAL_CERT("Delete internal TLS certificate"),
    ADD_LOCAL_GROUP("Add group"),
    EDIT_LOCAL_GROUP_DESC("Edit group description"),
    ADD_LOCAL_GROUP_MEMBERS("Add members to group"),
    REMOVE_LOCAL_GROUP_MEMBERS("Remove members from group"),
    DELETE_LOCAL_GROUP("Delete group"),

    // system parameters
    GENERATE_INTERNAL_TLS_CSR("Generate certificate request for TLS"),
    IMPORT_INTERNAL_TLS_CERT("Import TLS certificate from file"),
    UPLOAD_ANCHOR("Upload configuration anchor"),
    RE_CREATE_ANCHOR("Re-create configuration anchor"),
    RE_CREATE_INTERNAL_CONFIGURATION_ANCHOR("Re-create internal configuration anchor"),
    RE_CREATE_EXTERNAL_CONFIGURATION_ANCHOR("Re-create external configuration anchor"),
    ADD_TRUSTED_ANCHOR("Add trusted anchor"),
    DELETE_TRUSTED_ANCHOR("Delete trusted anchor"),
    UPLOAD_CONFIGURATION_PART("Upload configuration part"),
    ADD_TSP("Add timestamping service"),
    DELETE_TSP("Delete timestamping service"),
    EDIT_TIMESTAMP_SERVICE("Edit timestamping service"),
    GENERATE_INTERNAL_TLS_KEY_CERT("Generate new internal TLS key and certificate"),
    EDIT_CENTRAL_SERVER_ADDRESS("Edit central server address"),

    // keys and certificates events
    LOGIN_TOKEN("Log in to token"),
    LOGOUT_TOKEN("Log out from token"),
    CHANGE_PIN_TOKEN("Change token pin"),
    GENERATE_KEY("Generate key"),
    GENERATE_INTERNAL_CONFIGURATION_SIGNING_KEY("Generate internal configuration signing key"),
    GENERATE_EXTERNAL_CONFIGURATION_SIGNING_KEY("Generate external configuration signing key"),
    DELETE_KEY("Delete key"),
    DELETE_KEY_FROM_TOKEN_AND_CONFIG("Delete key from token and configuration"),
    GENERATE_CSR("Generate CSR"),
    DELETE_CSR("Delete CSR"),
    GENERATE_KEY_AND_CSR("Generate key and CSR"),
    ACTIVATE_SIGNING_KEY("Activate signing key"),
    ACTIVATE_INTERNAL_CONFIGURATION_SIGNING_KEY("Activate internal configuration signing key"),
    ACTIVATE_EXTERNAL_CONFIGURATION_SIGNING_KEY("Activate external configuration signing key"),
    DELETE_SIGNING_KEY("Delete signing key"),
    DELETE_INTERNAL_CONFIGURATION_SIGNING_KEY("Delete internal configuration signing key"),
    DELETE_EXTERNAL_CONFIGURATION_SIGNING_KEY("Delete external configuration signing key"),

    IMPORT_CERT_FILE("Import certificate from file"),
    IMPORT_CERT_TOKEN("Import certificate from token"),
    DELETE_CERT("Delete certificate"),
    DELETE_CERT_FROM_CONFIG("Delete certificate from configuration"),
    DELETE_CERT_FROM_TOKEN("Delete certificate from token"),
    ACTIVATE_CERT("Enable certificate"),
    DISABLE_CERT("Disable certificate"),
    REGISTER_AUTH_CERT("Register authentication certificate"),
    UNREGISTER_AUTH_CERT("Unregister authentication certificate"),
    SKIP_UNREGISTER_AUTH_CERT("Skip unregistration of authentication certificate"),
    UPDATE_TOKEN_NAME("Set friendly name to token"),
    UPDATE_KEY_NAME("Set friendly name to key"),
    DELETE_SECURITY_SERVER_AUTH_CERT("Delete authentication certificate of security server"),

    // backup and restore events
    BACKUP("Back up configuration"),
    UPLOAD_BACKUP("Upload backup file"),
    DELETE_BACKUP("Delete backup file"),
    RESTORE_BACKUP("Restore configuration"),

    UNSPECIFIED_ACCESS_CHECK("Access check"), // for AccessDeniedExceptions without more specific detail
    UNSPECIFIED_AUTHENTICATION("Authentication"),  // for AuthenticationExceptions without more specific detail

    ADD_MEMBER_CLASS("Add member class"),
    DELETE_MEMBER_CLASS("Delete member class"),
    EDIT_MEMBER_CLASS("Edit member class description"),

    ADD_MANAGEMENT_REQUEST("Add management request"),
    REVOKE_MANAGEMENT_REQUEST("Revoke management request"),
    DECLINE_MANAGEMENT_REQUEST("Decline management request"),
    APPROVE_MANAGEMENT_REQUEST("Approve management request"),

    DELETE_SECURITY_SERVER("Delete security server"),
    EDIT_SECURITY_SERVER_ADDRESS("Edit security server address"),

    ADD_CERTIFICATION_SERVICE("Add certification service"),
    DELETE_CERTIFICATION_SERVICE("Delete certification service"),
    EDIT_CERTIFICATION_SERVICE_SETTINGS("Edit certification service settings"),
    ADD_CERTIFICATION_SERVICE_OCSP_RESPONDER("Add OCSP responder of certification service"),
    ADD_CERTIFICATION_SERVICE_INTERMEDIATE_CA("Add intermediate CA"),
    ADD_INTERMEDIATE_CA_OCSP_RESPONDER("Add OCSP responder of intermediate CA"),
    EDIT_OCSP_RESPONDER("Edit OCSP responder"),
    ADD_GLOBAL_GROUP("Add global group"),
    ADD_GLOBAL_GROUP_MEMBERS("Add members to global group"),
    DELETE_GLOBAL_GROUP("Delete global group"),
    DELETE_GLOBAL_GROUP_MEMBER("Remove members from global group"),

    EDIT_GLOBAL_GROUP_DESCRIPTION("Edit global group description"),

    ADD_MEMBER("Add member"),
    DELETE_MEMBER("Delete member"),
    EDIT_MEMBER_NAME("Edit member name"),

    ADD_SUBSYSTEM("Add subsystem"),
    UNREGISTER_SUBSYSTEM("Unregister subsystem as security server client"),
    DELETE_SUBSYSTEM("Delete subsystem"),
    DELETE_INTERMEDIATE_CA("Delete intermediate CA"),
    DELETE_OCSP_RESPONDER("Delete OCSP responder"),

    REGISTER_MANAGEMENT_SERVICES_PROVIDER("Register management service provider as security server client"),
    EDIT_MANAGEMENT_SERVICES_PROVIDER("Edit provider of management services");
    private final String eventName;

    RestApiAuditEvent(String eventName) {
        this.eventName = eventName;
    }

    public String getEventName() {
        return eventName;
    }
}
