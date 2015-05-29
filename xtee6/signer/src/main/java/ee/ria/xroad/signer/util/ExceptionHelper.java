package ee.ria.xroad.signer.util;

import ee.ria.xroad.common.CodedException;

import static ee.ria.xroad.common.ErrorCodes.*;

/**
 * Contains helper methods for constructing CodedExceptions which are used in
 * multiple places.
 */
public final class ExceptionHelper {

    private ExceptionHelper() {
    }

    /**
     * @param tokenId the token id
     * @return exception indicating a token is not found
     */
    public static CodedException tokenNotFound(String tokenId) {
        return CodedException.tr(X_TOKEN_NOT_FOUND,
                "token_not_found", "Token '%s' not found", tokenId);
    }

    /**
     * @param tokenId the token id
     * @return exception indicating a token is not active
     */
    public static CodedException tokenNotActive(String tokenId) {
        return CodedException.tr(X_TOKEN_NOT_ACTIVE,
                "token_not_active", "Token '%s' not active", tokenId);
    }

    /**
     * @param tokenId the token id
     * @return exception indicating a token is not initialized
     */
    public static CodedException tokenNotInitialized(String tokenId) {
        return CodedException.tr(X_TOKEN_NOT_INITIALIZED,
                "token_not_initialized", "Token '%s' not initialized", tokenId);
    }

    /**
     * @param tokenId the token id
     * @return exception indicating a token is not available
     */
    public static CodedException tokenNotAvailable(String tokenId) {
        return CodedException.tr(X_TOKEN_NOT_AVAILABLE,
                "token_not_available", "Token '%s' not available", tokenId);
    }

    /**
     * @param keyId the key id
     * @return exception indicating a key is not found
     */
    public static CodedException keyNotFound(String keyId) {
        return CodedException.tr(X_KEY_NOT_FOUND,
                "key_not_found", "Key '%s' not found", keyId);
    }

    /**
     * @param keyId the key id
     * @return exception indicating a key is not available
     */
    public static CodedException keyNotAvailable(String keyId) {
        return CodedException.tr(X_KEY_NOT_FOUND,
                "key_not_available", "Key '%s' not available", keyId);
    }

    /**
     * @param certId the certificate id
     * @return exception indicating a certificate is not found
     */
    public static CodedException certWithIdNotFound(String certId) {
        return CodedException.tr(X_CERT_NOT_FOUND,
                "cert_with_id_not_found",
                "Certificate with id '%s' not found", certId);
    }

    /**
     * @param message the message
     * @return exception indicating login to token failed
     */
    public static CodedException loginFailed(String message) {
        return CodedException.tr(X_LOGIN_FAILED,
                "login_failed", "Login failed: %s", message);
    }

    /**
     * @param message the message
     * @return  exception indicating logout of a token failed
     */
    public static CodedException logoutFailed(String message) {
        return CodedException.tr(X_LOGOUT_FAILED,
                "logout_failed", "Logout failed: %s", message);
    }
}
