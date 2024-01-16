/*
 * The MIT License
 * <p>
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
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

package org.niis.xroad.cs.admin.core.validation;

import com.google.common.net.InetAddresses;
import org.niis.xroad.common.exception.ValidationFailureException;
import org.springframework.stereotype.Component;

import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.INVALID_IP_ADDRESS;

@Component
public class IpAddressValidator {

    public void validateIpAddress(String ipAddress) {
        if (!InetAddresses.isInetAddress(ipAddress)) {
            throw new ValidationFailureException(INVALID_IP_ADDRESS);
        }
    }


    public void validateCommaSeparatedIpAddresses(String commaSeparatedIpAddresses) {
        String[] ipAddresses = commaSeparatedIpAddresses.split(",");
        for (String ipAddress : ipAddresses) {
            validateIpAddress(ipAddress.trim());
        }
    }

}
