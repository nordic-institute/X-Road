package org.niis.xroad.restapi.service;

import org.niis.xroad.restapi.exceptions.ErrorDeviation;

/**
 * General error that happens when importing a cert. Usually a wrong file type
 */
public class InvalidCertificateException extends ServiceException {
    public static final String INVALID_CERT = "invalid_cert";

    public InvalidCertificateException(Throwable t) {
        super(t, new ErrorDeviation(INVALID_CERT));
    }

    public InvalidCertificateException(String msg, Throwable t) {
        super(msg, t, new ErrorDeviation(INVALID_CERT));
    }
}
