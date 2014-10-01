package ee.cyber.sdsb.signer.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import ee.cyber.sdsb.common.CodedException;

import static ee.cyber.sdsb.common.ErrorCodes.*;

/**
 * Contains helper methods for constructing CodedExceptions which are used in
 * multiple places.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ExceptionHelper {

    public static CodedException tokenNotFound(String tokenId) {
        return CodedException.tr(X_TOKEN_NOT_FOUND,
                "token_not_found", "Token '%s' not found", tokenId);
    }

    public static CodedException tokenNotActive(String tokenId) {
        return CodedException.tr(X_TOKEN_NOT_ACTIVE,
                "token_not_active", "Token '%s' not active", tokenId);
    }

    public static CodedException tokenNotInitialized(String tokenId) {
        return CodedException.tr(X_TOKEN_NOT_INITIALIZED,
                "token_not_initialized", "Token '%s' not initialized", tokenId);
    }

    public static CodedException keyNotFound(String keyId) {
        return CodedException.tr(X_KEY_NOT_FOUND,
                "key_not_found", "Key '%s' not found", keyId);
    }

    public static CodedException keyNotAvailable(String keyId) {
        return CodedException.tr(X_KEY_NOT_FOUND,
                "key_not_available", "Key '%s' not available", keyId);
    }

    public static CodedException certWithIdNotFound(String certId) {
        return CodedException.tr(X_CERT_NOT_FOUND,
                "cert_with_id_not_found",
                "Certificate with id '%s' not found", certId);
    }

    public static CodedException loginFailed(String message) {
        return CodedException.tr(X_LOGIN_FAILED,
                "login_failed", "Login failed: %s", message);
    }

    public static CodedException logoutFailed(String message) {
        return CodedException.tr(X_LOGOUT_FAILED,
                "logout_failed", "Logout failed: %s", message);
    }
}
