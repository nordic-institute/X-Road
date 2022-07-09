/**
 * The MIT License
 *
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package org.niis.xroad.centralserver.restapi.service.exception;

import lombok.Getter;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;

import java.util.Arrays;

public enum ErrorMessage {
    MEMBER_CLASS_IS_IN_USE("Cannot delete member class: Found X-Road members belonging to the class."
            + " Only classes with no registered members can be deleted."),
    MEMBER_CLASS_NOT_FOUND("No member class with the specified code found."),
    MEMBER_CLASS_EXISTS("Member class with the same code already exists."),
    MEMBER_NOT_FOUND("No member with the specified code found."),
    MEMBER_EXISTS("Member with the same code already exists."),

    CLIENT_NOT_FOUND("No client with the specified identifier found."),

    CLIENT_EXISTS("Client with the same identifier already exists."),
    MANAGEMENT_REQUEST_NOT_FOUND("No management request with the specified id found."),
    MANAGEMENT_REQUEST_EXISTS("A pending management request already exists."),
    MANAGEMENT_REQUEST_SECURITY_SERVER_EXISTS("Certificate is already registered."),
    INVALID_AUTH_CERTIFICATE("Invalid authentication certificate"),
    MANAGEMENT_REQUEST_INVALID_STATE_FOR_APPROVAL("Management request can not be approved"),
    MANAGEMENT_REQUEST_SERVER_OWNER_NOT_FOUND("Security server owner not found"),
    MANAGEMENT_REQUEST_INVALID_STATE("Requested operation can not be applied in this state"),
    MANAGEMENT_REQUEST_NOT_SUPPORTED(("Unknown management request type")),
    MANAGEMENT_REQUEST_SERVER_NOT_FOUND("Security server not found"),
    MANAGEMENT_REQUEST_CANNOT_REGISTER_OWNER("Cannot register owner as a client"),
    MANAGEMENT_REQUEST_MEMBER_NOT_FOUND("Member does not exist"),
    MANAGEMENT_REQUEST_CLIENT_REGISTRATION_NOT_FOUND("Client registration does not exist"),
    MANAGEMENT_REQUEST_ALREADY_REGISTERED("Client already registered to a server");

    @Getter
    final String description;

    ErrorMessage(String description) {
        this.description = description;
    }

    public ErrorDeviation asDeviation(String... metadataItem) {
        if (metadataItem != null && metadataItem.length > 0) {
            return new ErrorDeviation(name().toLowerCase(), Arrays.asList(metadataItem));
        } else {
            return new ErrorDeviation(name().toLowerCase());
        }
    }
}
