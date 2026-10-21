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

package org.niis.xroad.cs.admin.core.validation;

import com.google.common.net.InetAddresses;
import com.google.common.net.InternetDomainName;
import org.niis.xroad.common.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.INVALID_DNS_NAME;

@Component
public class DnsNameValidator {

    private static final String LOCALHOST = "localhost";
    private static final String LOCALHOST_SUFFIX = "." + LOCALHOST;
    private static final Pattern ASCII_HOST_CHARACTERS = Pattern.compile("[A-Za-z0-9.-]+");

    public void validate(String host) {
        if (!isValid(host)) {
            throw new BadRequestException(INVALID_DNS_NAME.build());
        }
    }

    public boolean isValid(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        if (!ASCII_HOST_CHARACTERS.matcher(host).matches()) {
            // Guava's InternetDomainName maps Unicode full stops and other confusables onto ASCII
            // before validating, so checking the raw input here rejects what Guava would otherwise
            // silently normalise and accept.
            return false;
        }
        if (host.endsWith(".")) {
            // Valid DNS syntax for the root label, but Guava's InternetDomainName silently
            // strips it rather than rejecting it. Rejecting it here keeps one literal string
            // per host instead of two that mean the same thing.
            return false;
        }
        var lowerCaseHost = host.toLowerCase(Locale.ROOT);
        if (lowerCaseHost.equals(LOCALHOST) || lowerCaseHost.endsWith(LOCALHOST_SUFFIX)) {
            return false;
        }
        if (InetAddresses.isInetAddress(host)) {
            return false;
        }
        return InternetDomainName.isValid(host);
    }
}
