package org.niis.xroad.centralserver.restapi.service.exception;

import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.service.ServiceException;

import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_SOFTWARE_TOKEN_INIT_FAILED;

/**
 * If the software token init fails
 */
public class SoftwareTokenInitException extends ServiceException {
    public SoftwareTokenInitException(String msg, Throwable t) {
        super(msg, t, new ErrorDeviation(ERROR_SOFTWARE_TOKEN_INIT_FAILED));
    }
}


