/*
 * The MIT License
 *
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

import org.junit.jupiter.api.Test;
import org.niis.xroad.common.exception.ValidationFailureException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class IpAddressValidatorTest {

    private final IpAddressValidator ipAddressValidator = new IpAddressValidator();

    @Test
    void validateIpAddressThrowsException() {
        assertThatThrownBy(() -> ipAddressValidator.validateIpAddress("")).isInstanceOf(ValidationFailureException.class);
        assertThatThrownBy(() -> ipAddressValidator.validateIpAddress("1.2.3.4.")).isInstanceOf(
                ValidationFailureException.class);
        assertThatThrownBy(() -> ipAddressValidator.validateIpAddress("1.2.03.4")).isInstanceOf(
                ValidationFailureException.class);
        assertThatThrownBy(() -> ipAddressValidator.validateIpAddress("1.2.3.X")).isInstanceOf(
                ValidationFailureException.class);
        assertThatThrownBy(() -> ipAddressValidator.validateIpAddress("1.2.333.4")).isInstanceOf(
                ValidationFailureException.class);
        assertThatThrownBy(() -> ipAddressValidator.validateIpAddress("1:2:3:4:5:6:7")).isInstanceOf(
                ValidationFailureException.class);
        assertThatThrownBy(() -> ipAddressValidator.validateIpAddress("1:2:3:4:5:6:7:8:")).isInstanceOf(
                ValidationFailureException.class);
        assertThatThrownBy(() -> ipAddressValidator.validateIpAddress("a:b:c:d:e:f:1:g")).isInstanceOf(
                ValidationFailureException.class);
        assertThatThrownBy(() -> ipAddressValidator.validateIpAddress("a:b:c:::1:g")).isInstanceOf(
                ValidationFailureException.class);
    }

    @Test
    void validateIpAddress() {
        ipAddressValidator.validateIpAddress("1.2.3.4");
        ipAddressValidator.validateIpAddress("1:2:3:4:5:6:7:8");
        ipAddressValidator.validateIpAddress("1:2:3::5:6:7:8");
    }

    @Test
    void validateCommaSeparatedIpAddressesThrowsException() {
        assertThatThrownBy(() -> ipAddressValidator.validateIpAddress("1.2.3.4,,1:2:3:4:5:6:7:8")).isInstanceOf(
                ValidationFailureException.class);
        assertThatThrownBy(() -> ipAddressValidator.validateIpAddress("1.2.3.4,1:2:3:4:5:6:7:8,")).isInstanceOf(
                ValidationFailureException.class);
        assertThatThrownBy(() -> ipAddressValidator.validateIpAddress("1.2.a.4,1:2:3:4:5:6:7:8")).isInstanceOf(
                ValidationFailureException.class);
        assertThatThrownBy(() -> ipAddressValidator.validateIpAddress("1.2.3.4,1:2:3:4:5:6:7.8")).isInstanceOf(
                ValidationFailureException.class);
    }

    @Test
    void validateCommaSeparatedIpAddresses() {
        ipAddressValidator.validateCommaSeparatedIpAddresses("1.2.3.4");
        ipAddressValidator.validateCommaSeparatedIpAddresses("1:2:3:4:5:6:7:8");
        ipAddressValidator.validateCommaSeparatedIpAddresses("1.2.3.4,1:2:3:4:5:6:7:8");
        ipAddressValidator.validateCommaSeparatedIpAddresses("1.2.3.4, 1:2:3:4:5:6:7:8");
        ipAddressValidator.validateCommaSeparatedIpAddresses("1.2.3.4,1:2:3:4:5:6:7:8,1:2::b:ff");
    }
}
