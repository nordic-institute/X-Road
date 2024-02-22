/*
 * The MIT License
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
package org.niis.xroad.restapi.converter;

import ee.ria.xroad.common.identifier.SecurityServerId;

import org.junit.jupiter.api.Test;
import org.niis.xroad.common.exception.ValidationFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * test SecurityServerConverter
 */
class SecurityServerIdConverterTest {

    SecurityServerIdConverter securityServerIdConverter = new SecurityServerIdConverter();

    @Test
    void convertEncodedId() {
        String securityServerCode = "security-server-foo";
        String memberCode = "XRD2:GOV:M4";
        SecurityServerId id = securityServerIdConverter.fromDto(
                memberCode + ":" + securityServerCode);
        assertEquals("XRD2", id.getXRoadInstance());
        assertEquals("GOV", id.getMemberClass());
        assertEquals("M4", id.getMemberCode());
        assertEquals(securityServerCode, id.getServerCode());

        String difficultServerCode = "FOO SS-;/?@=&-X<!-- o -->BAR";
        id = securityServerIdConverter.fromDto(
                memberCode + ":" + difficultServerCode);
        assertEquals("XRD2", id.getXRoadInstance());
        assertEquals("GOV", id.getMemberClass());
        assertEquals("M4", id.getMemberCode());
        assertEquals(difficultServerCode, id.getServerCode());
    }

    @Test
    void convertEncodedIdWithSubsystem() {
        assertThatExceptionOfType(ValidationFailureException.class)
                .isThrownBy(() -> securityServerIdConverter.fromDto("XRD2:GOV:M4:SS1:serverCode"));
    }

    @Test
    void convertEncodedIdWithMissingMember() {
        assertThatExceptionOfType(ValidationFailureException.class)
                .isThrownBy(() -> securityServerIdConverter.fromDto("XRD2:GOV:serverCode"));
    }

    @Test
    void convertEncodedIdWithTooManyElements() {
        assertThatExceptionOfType(ValidationFailureException.class)
                .isThrownBy(() -> securityServerIdConverter.fromDto("XRD2:GOV:M4:SS1:serverCode::::"));
    }

    @Test
    void convertEmptyEncodedId() {
        assertThatExceptionOfType(ValidationFailureException.class)
                .isThrownBy(() -> securityServerIdConverter.fromDto(""));
    }

    @Test
    void convertNullEncodedId() {
        String id = null;
        var result = securityServerIdConverter.fromDto(id);
        assertThat(result).isNull();
    }

    @Test
    void convertEncodedIdWithoutDelimiter() {
        assertThatExceptionOfType(ValidationFailureException.class)
                .isThrownBy(() -> securityServerIdConverter.fromDto(";;;;asdsdas"));
    }

    @Test
    void convertSecurityServerId() {
        SecurityServerId securityServerId = SecurityServerId.Conf.create(
                "XRD2", "GOV", "M4", "server1");
        String id = securityServerIdConverter.toDto(securityServerId);
        assertEquals("XRD2:GOV:M4:server1", id);
    }

}
