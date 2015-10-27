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
