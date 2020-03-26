package org.niis.xroad.restapi.service;

import org.niis.xroad.restapi.exceptions.ErrorDeviation;

public class EndpointNotFoundException extends NotFoundException {
    public static final String ERROR_ENDPOINT_NOT_FOUND = "endpoint_not_found";
    private static final String MESSAGE = "Endpoint not found with id: %s";

    public EndpointNotFoundException(String id) {
        super(String.format(MESSAGE, id), new ErrorDeviation(ERROR_ENDPOINT_NOT_FOUND, id));
    }
}
