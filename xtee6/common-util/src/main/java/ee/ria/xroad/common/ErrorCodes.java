/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package ee.ria.xroad.common;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.net.UnknownServiceException;
import java.nio.channels.UnresolvedAddressException;
import java.security.cert.CertificateException;

import javax.xml.soap.SOAPException;

import org.apache.james.mime4j.MimeException;
import org.xml.sax.SAXException;

/** Enumeration class for various error codes. */
public final class ErrorCodes {

    // Error code prefixes

    public static final String SERVER_SERVERPROXY_X = "Server.ServerProxy";
    public static final String CLIENT_X = "Client";
    public static final String SERVER_CLIENTPROXY_X = "Server.ClientProxy";
    public static final String SIGNER_X = "Signer";

    // Generic errors.

    public static final String X_IO_ERROR = "IOError";
    public static final String X_NETWORK_ERROR = "NetworkError";
    public static final String X_INTERNAL_ERROR = "InternalError";
    public static final String X_BAD_REQUEST = "BadRequest";
    public static final String X_NOT_FOUND = "NotFound";
    public static final String X_HTTP_ERROR = "HttpError";
    public static final String X_DATABASE_ERROR = "DatabaseError";
    public static final String X_INVALID_RESPONSE = "InvalidResponse";
    public static final String X_INVALID_REQUEST = "InvalidRequest";


    // Verification errors

    public static final String X_CANNOT_CREATE_SIGNATURE =
            "CannotCreateSignature";
    public static final String X_CERT_VALIDATION = "CertValidation";
    public static final String X_MISSING_VALIDATION_INFO =
            "MissingValidationInfo";
    public static final String X_INCORRECT_VALIDATION_INFO =
            "IncorrectValidationInfo";
    public static final String X_CANNOT_CREATE_CERT_PATH =
            "CannotCreateCertPath";
    public static final String X_CERT_VALIDITY_TIME = "CertValidityTime";
    public static final String X_INCORRECT_CERTIFICATE = "IncorrectCertificate";
    public static final String X_UNSUPPORTED_ALGORITHM = "UnsupportedAlgorithm";
    public static final String X_INVALID_SIGNATURE_VALUE = "InvalidSignatureValue";
    public static final String X_MALFORMED_SIGNATURE = "MalformedSignature";
    public static final String X_INVALID_XML = "InvalidXml";
    public static final String X_INVALID_REFERENCE = "InvalidReference";
    public static final String X_INVALID_CERT_PATH_X = "InvalidCertPath";
    public static final String X_SIGNATURE_VERIFICATION_X =
            "SignatureVerification";
    public static final String X_MALFORMED_SOAP = "MalformedSoap";
    public static final String X_TIMESTAMP_VALIDATION = "TimestampValidation";
    public static final String X_INVALID_HASH_CHAIN_RESULT = "InvalidHashChain";
    public static final String X_MALFORMED_HASH_CHAIN = "MalformedHashChain";
    public static final String X_HASHCHAIN_UNUSED_INPUTS = "HashChainUnusedInputs";
    public static final String X_INVALID_HASH_CHAIN_REF = "InvalidHashChainRef";


    // Message processing errors

    public static final String X_SSL_AUTH_FAILED = "SslAuthenticationFailed";
    public static final String X_LOGGING_FAILED_X = "LoggingFailed";
    public static final String X_TIMESTAMPING_FAILED_X = "TimestampingFailed";
    public static final String X_INVALID_CONTENT_TYPE = "InvalidContentType";
    public static final String X_INVALID_HTTP_METHOD = "InvalidHttpMethod";
    public static final String X_INVALID_MESSAGE = "InvalidMessage";
    public static final String X_INVALID_SECURITY_SERVER = "InvalidSecurityServer";
    public static final String X_MIME_PARSING_FAILED = "MimeParsingFailed";
    public static final String X_MISSING_HEADER = "MissingHeader";
    public static final String X_MISSING_HEADER_FIELD = "MissingHeaderField";
    public static final String X_DUPLICATE_HEADER_FIELD = "DuplicateHeaderField";
    public static final String X_MISSING_BODY = "MissingBody";
    public static final String X_INVALID_BODY = "InvalidBody";
    public static final String X_INCONSISTENT_HEADERS = "InconsistentHeaders";
    public static final String X_INCONSISTENT_RESPONSE = "InconsistentResponse";
    public static final String X_MISSING_SOAP = "MissingSoap";
    public static final String X_INVALID_SOAP = "InvalidSoap";
    public static final String X_ACCESS_DENIED = "AccessDenied";
    public static final String X_SERVICE_DISABLED = "ServiceDisabled";
    public static final String X_SERVICE_FAILED_X = "ServiceFailed";
    public static final String X_MISSING_SIGNATURE = "MissingSignature";
    public static final String X_UNKNOWN_SERVICE = "UnknownService";
    public static final String X_SECURITY_CATEGORY = "SecurityCategory";
    public static final String X_INVALID_PROTOCOL_VERSION =
            "InvalidProtocolVersion";

    // ASiC container related errors

    public static final String X_ASIC_INVALID_CONTAINER = "AsicInvalidContainer";
    public static final String X_ASIC_MIME_TYPE_NOT_FOUND = "AsicMimeTypeNotFound";
    public static final String X_ASIC_SIGNATURE_NOT_FOUND = "AsicSignatureNotFound";
    public static final String X_ASIC_MESSAGE_NOT_FOUND = "AsicMessageNotFound";
    public static final String X_ASIC_INVALID_MIME_TYPE = "AsicInvalidMimeType";
    public static final String X_ASIC_HASH_CHAIN_RESULT_NOT_FOUND =
            "AsicHashChainResultNotFound";
    public static final String X_ASIC_HASH_CHAIN_NOT_FOUND =
            "AsicHashChainNotFound";
    public static final String X_ASIC_TIMESTAMP_NOT_FOUND =
            "AsicTimestampNotFound";
    public static final String X_ASIC_MANIFEST_NOT_FOUND =
            "AsicManifestNotFound";


    // Configuration errors

    public static final String X_UNKNOWN_MEMBER = "UnknownMember";
    public static final String X_MALFORMED_SERVERCONF = "MalformedServerConf";
    public static final String X_MALFORMED_KEYCONF = "MalformedKeyConf";
    public static final String X_MALFORMED_GLOBALCONF = "MalformedGlobalConf";
    public static final String X_MALFORMED_OPTIONAL_PARTS_CONF =
            "MalformedOptionalPartsConf";
    public static final String X_OUTDATED_GLOBALCONF = "OutdatedGlobalConf";
    public static final String X_SERVICE_MISSING_URL = "ServiceMissingUrl";
    public static final String X_SERVICE_MALFORMED_URL = "ServiceMalformedUrl";
    public static final String X_ADAPTER_WSDL_NOT_FOUND = "AdapterWsdlNotFound";


    // Signer Errors

    public static final String X_KEY_NOT_FOUND = "KeyNotFound";
    public static final String X_KEY_NOT_AVAILABLE = "KeyNotAvailable";
    public static final String X_CERT_NOT_FOUND = "CertNotFound";
    public static final String X_CSR_NOT_FOUND = "CsrNotFound";
    public static final String X_TOKEN_NOT_FOUND = "TokenNotFound";
    public static final String X_TOKEN_NOT_ACTIVE = "TokenNotActive";
    public static final String X_TOKEN_NOT_INITIALIZED = "TokenNotInitialized";
    public static final String X_TOKEN_NOT_AVAILABLE = "TokenNotAvailable";
    public static final String X_TOKEN_READONLY = "TokenReadOnly";
    public static final String X_CANNOT_SIGN = "CannotSign";
    public static final String X_CSR_FAILED =
            "FailedToGenerateCertificateRequest";
    public static final String X_FAILED_TO_GENERATE_R_KEY =
            "FailedToGeneratePrivateKey";
    public static final String X_FAILED_TO_GENERATE_U_KEY =
            "FailedToGeneratePublicKey";
    public static final String X_FAILED_TO_DELETE_KEY = "FailedToDeleteKey";
    public static final String X_CERT_EXISTS = "CertExists";
    public static final String X_NO_MEMBERID_FROM_CERT =
            "CannotGetMemberIdFromCertificate";
    public static final String X_WRONG_CERT_USAGE = "WrongCertUsage";
    public static final String X_NO_MEMBERID = "CannotFindMember";
    public static final String X_LOGIN_FAILED = "LoginFailed";
    public static final String X_LOGOUT_FAILED = "LogoutFailed";
    public static final String X_PIN_INCORRECT = "PinIncorrect";
    public static final String X_CERT_IMPORT_FAILED = "CertImportFailed";
    public static final String X_TOKEN_PIN_POLICY_FAILURE = "TokenPinPolicyFailure";

    // SecureLog errors

    public static final String X_SLOG_MALFORMED_RECORD = "MalformedRecord";
    public static final String X_SLOG_MALFORMED_INDEX = "MalformedIndex";
    public static final String X_SLOG_MALFORMED_ARCHIVE = "MalformedArchive";
    public static final String X_SLOG_TIMESTAMPER_FAILED = "TimestamperFailed";
    public static final String X_SLOG_RECORD_NOT_FOUND = "RecordNotFound";


    /**
     * Translates technical exceptions to proxy exceptions with
     * the appropriate error code.
     * @param ex the exception
     * @return translated CodedException
     */
    public static CodedException translateException(Throwable ex) {
        if (ex instanceof CodedException) {
            return (CodedException) ex;
        } else if (ex instanceof UnknownHostException
                || ex instanceof MalformedURLException
                || ex instanceof SocketException
                || ex instanceof UnknownServiceException
                || ex instanceof UnresolvedAddressException) {
            return new CodedException(X_NETWORK_ERROR, ex);
        } else if (ex instanceof IOException) {
            return new CodedException(X_IO_ERROR, ex);
        } else if (ex instanceof CertificateException) {
            return new CodedException(X_INCORRECT_CERTIFICATE, ex);
        } else if (ex instanceof SOAPException) {
            return new CodedException(X_INVALID_SOAP, ex);
        } else if (ex instanceof MimeException) {
            return new CodedException(X_MIME_PARSING_FAILED, ex);
        } else if (ex instanceof SAXException) {
            return new CodedException(X_INVALID_XML, ex);
        } else { // other system exceptions.
            return new CodedException(X_INTERNAL_ERROR, ex);
        }
    }

    /**
     * Translates technical exceptions to proxy exceptions with
     * the appropriate error code. It also prepends the prefix
     * in front of error code.
     * @param prefix the prefix
     * @param ex the exception
     * @return translated exception with prefix
     */
    public static CodedException translateWithPrefix(String prefix,
            Throwable ex) {
        return translateException(ex).withPrefix(prefix);
    }

    private ErrorCodes() {
    }
}
