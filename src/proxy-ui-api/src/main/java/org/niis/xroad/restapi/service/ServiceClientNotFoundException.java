package org.niis.xroad.restapi.service;

import org.niis.xroad.restapi.exceptions.ErrorDeviation;

public class ServiceClientNotFoundException extends NotFoundException {
    public static final String ERROR_SERVICE_CLIENT_NOT_FOUND = "service_client_not_found";

    public ServiceClientNotFoundException(String s) {
        super(s, new ErrorDeviation(ERROR_SERVICE_CLIENT_NOT_FOUND));
    }
}
