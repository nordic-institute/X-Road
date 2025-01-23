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
package org.niis.xroad.signer.api.exception;

import ee.ria.xroad.common.CodedException;

import static ee.ria.xroad.common.ErrorCodes.SIGNER_X;
import static ee.ria.xroad.common.ErrorCodes.X_CERT_EXISTS;
import static ee.ria.xroad.common.ErrorCodes.X_CERT_NOT_FOUND;
import static ee.ria.xroad.common.ErrorCodes.X_CSR_NOT_FOUND;
import static ee.ria.xroad.common.ErrorCodes.X_INCORRECT_CERTIFICATE;
import static ee.ria.xroad.common.ErrorCodes.X_KEY_NOT_FOUND;
import static ee.ria.xroad.common.ErrorCodes.X_LOGIN_FAILED;
import static ee.ria.xroad.common.ErrorCodes.X_PIN_INCORRECT;
import static ee.ria.xroad.common.ErrorCodes.X_TOKEN_NOT_FOUND;
import static ee.ria.xroad.common.ErrorCodes.X_WRONG_CERT_USAGE;

public class SignerException extends CodedException {
    public static final String CKR_PIN_INCORRECT_MESSAGE = "Login failed: CKR_PIN_INCORRECT";

    public SignerException(CodedException ce) {
        super(ce.getFaultCode(), ce, ce.getFaultString());
        withPrefix(SIGNER_X);
        translationCode = ce.getTranslationCode();
        arguments = ce.getArguments();
    }

    public SignerException(String faultCode) {
        super(faultCode);
        withPrefix(SIGNER_X);
    }

    public SignerException(String faultCode, String faultMessage, Object... arguments) {
        super(faultCode, faultMessage, arguments);
        withPrefix(SIGNER_X);
    }


    public SignerException(String faultCode, Throwable cause) {
        super(faultCode, cause);
        withPrefix(SIGNER_X);
    }

    public SignerException(String faultCode, String faultMessage, Throwable cause) {
        super(faultCode, cause, faultMessage);
        withPrefix(SIGNER_X);
    }

    private boolean isCausedBy(String faultCode) {
        return getFaultCode().endsWith("." + faultCode);
    }

    public boolean isCausedByCertNotFound() {
        return isCausedBy(X_CERT_NOT_FOUND);
    }

    public boolean isCausedByTokenNotFound() {
        return isCausedBy(X_TOKEN_NOT_FOUND);
    }

    public boolean isCausedByKeyNotFound() {
        return isCausedBy(X_KEY_NOT_FOUND);
    }

    public boolean isCausedByCsrNotFound() {
        return isCausedBy(X_CSR_NOT_FOUND);
    }

    public boolean isCausedByCertificateWrongUsage() {
        return isCausedBy(X_WRONG_CERT_USAGE);
    }

    public boolean isCausedByIncorrectCertificate() {
        return isCausedBy(X_INCORRECT_CERTIFICATE);
    }

    public boolean isCausedByDuplicateCertificate() {
        return isCausedBy(X_CERT_EXISTS);
    }

    public boolean isCausedByIncorrectPin() {
        if (isCausedBy(X_PIN_INCORRECT)) {
            return true;
        } else if (isCausedBy(X_LOGIN_FAILED)) {
            // only way to detect HSM pin incorrect is by matching to codedException
            // fault string.
            return CKR_PIN_INCORRECT_MESSAGE.equals(getFaultString());
        }
        return false;
    }

    /**
     * Creates new exception with translation code for i18n.
     * @param faultCode the fault code
     * @param trCode the translation code
     * @param faultMessage the message
     * @return CodedException
     */
    public static SignerException tr(String faultCode, String trCode,
                                     String faultMessage) {
        SignerException ret = new SignerException(faultCode, faultMessage);

        ret.translationCode = trCode;

        return ret;
    }

    public static SignerException tr(String faultCode, String trCode,
                                     String faultMessage, Object... args) {
        SignerException ret = new SignerException(faultCode, faultMessage, args);

        ret.translationCode = trCode;

        return ret;
    }

}
