package org.niis.xroad.restapi.service;

import org.niis.xroad.restapi.exceptions.ErrorDeviation;

public class EndpointAlreadyExistsException extends ServiceException {
    public static final String ERROR_EXISTING_ENDPOINT = "endpoint_already_exists";

    public EndpointAlreadyExistsException(String msg) {
        super(new ErrorDeviation(ERROR_EXISTING_ENDPOINT, msg));
    }
}
