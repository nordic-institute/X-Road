/*
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
package org.niis.xroad.common.managementrequest.model;

import ee.ria.xroad.common.CodedException;

import lombok.Getter;

import static ee.ria.xroad.common.ErrorCodes.X_INVALID_REQUEST;

public enum ManagementRequestType {
    AUTH_CERT_REGISTRATION_REQUEST("authCertReg"),
    CLIENT_REGISTRATION_REQUEST("clientReg"),
    OWNER_CHANGE_REQUEST("ownerChange"),
    CLIENT_DELETION_REQUEST("clientDeletion"),
    AUTH_CERT_DELETION_REQUEST("authCertDeletion"),
    ADDRESS_CHANGE_REQUEST("addressChange"),
    CLIENT_DISABLE_REQUEST("clientDisable"),
    CLIENT_ENABLE_REQUEST("clientEnable");

    @Getter
    private final String serviceCode;

    ManagementRequestType(String serviceCode) {
        this.serviceCode = serviceCode;
    }

    public static ManagementRequestType getByServiceCode(String serviceCode) {
        for (ManagementRequestType requestType : values()) {
            if (requestType.getServiceCode().equalsIgnoreCase(serviceCode)) {
                return requestType;
            }
        }
        throw new CodedException(X_INVALID_REQUEST, "Unknown service code '%.20s'", serviceCode);
    }
}
