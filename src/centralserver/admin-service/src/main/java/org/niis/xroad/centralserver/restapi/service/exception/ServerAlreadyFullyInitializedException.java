package org.niis.xroad.centralserver.restapi.service.exception;

import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.service.ServiceException;

import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_SERVER_ALREADY_FULLY_INITIALIZED;

/**
 * If the server has already been fully initialized
 */
public class ServerAlreadyFullyInitializedException extends ServiceException {
    public ServerAlreadyFullyInitializedException(String msg) {
        super(msg, new ErrorDeviation(ERROR_SERVER_ALREADY_FULLY_INITIALIZED));
    }
}
