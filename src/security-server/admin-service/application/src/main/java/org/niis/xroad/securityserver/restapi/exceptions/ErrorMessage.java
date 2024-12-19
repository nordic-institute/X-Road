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
package org.niis.xroad.securityserver.restapi.exceptions;

import lombok.Getter;
import org.niis.xroad.restapi.exceptions.DeviationProvider;

import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_AUTH_CERT_NOT_SUPPORTED;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_CERTIFICATE_NOT_FOUND;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_CERTIFICATE_NOT_FOUND_WITH_ID;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_CERTIFICATE_WRONG_USAGE;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_SIGN_CERT_NOT_SUPPORTED;

@Getter
public enum ErrorMessage implements DeviationProvider {
    CERTIFICATE_WRONG_USAGE(ERROR_CERTIFICATE_WRONG_USAGE, "Certificate has wrong usage"),
    AUTH_CERT_NOT_SUPPORTED(ERROR_AUTH_CERT_NOT_SUPPORTED, "Not supported authentication certificate"),
    SIGN_CERT_NOT_SUPPORTED(ERROR_SIGN_CERT_NOT_SUPPORTED, "Not supported sign certificate"),
    CERTIFICATE_NOT_FOUND(ERROR_CERTIFICATE_NOT_FOUND, "Certificate not found"),
    CERTIFICATE_NOT_FOUND_WITH_ID(ERROR_CERTIFICATE_NOT_FOUND_WITH_ID, "Certificate not found by id");

    private final String code;
    private final String description;

    ErrorMessage(final String code, final String description) {
        this.code = code;
        this.description = description;
    }
}
