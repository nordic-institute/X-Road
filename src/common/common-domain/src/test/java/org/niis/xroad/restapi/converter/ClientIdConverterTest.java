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

import ee.ria.xroad.common.identifier.ClientId;

import org.junit.jupiter.api.Test;
import org.niis.xroad.common.exception.ValidationFailureException;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * test ClientConverter
 */
class ClientIdConverterTest {
    private final ClientIdConverter clientIdConverter = new ClientIdConverter();

    @Test
    void convertStringId() {
        ClientId clientId = clientIdConverter.convertId("XRD2:GOV:M4:SS1");
        assertEquals("XRD2", clientId.getXRoadInstance());
        assertEquals("GOV", clientId.getMemberClass());
        assertEquals("M4", clientId.getMemberCode());
        assertEquals("SS1", clientId.getSubsystemCode());

        clientId = clientIdConverter.convertId("XRD2:GOV:M4");
        assertNull(clientId.getSubsystemCode());
    }

    @Test
    void convertDifficultStringId() {
        String difficultSubsystemId = "FOO SS-;/?@=&-X<!-- o -->BAR";
        ClientId clientId = clientIdConverter.convertId("XRD2:GOV:M4:" + difficultSubsystemId);
        assertEquals("XRD2", clientId.getXRoadInstance());
        assertEquals("GOV", clientId.getMemberClass());
        assertEquals("M4", clientId.getMemberCode());
        assertEquals(difficultSubsystemId, clientId.getSubsystemCode());
    }

    @Test
    void convertBadStringId() {
        assertThatExceptionOfType(ValidationFailureException.class)
                .isThrownBy(() -> clientIdConverter.convertId("XRD2:GOV:M4:SS1:aa"));
    }

    @Test
    void convertBadStringId2() {
        assertThatExceptionOfType(ValidationFailureException.class)
                .isThrownBy(() -> clientIdConverter.convertId("XRD2"));
    }

    @Test
    void convertBadStringId3() {
        assertThatExceptionOfType(ValidationFailureException.class)
                .isThrownBy(() -> clientIdConverter.convertId("XRD2:GOV:M4:SS1::::::"));
    }

    @Test
    void isEncodedMemberId() {
        assertTrue(clientIdConverter.isEncodedMemberId("XRD2:GOV:M4"));
        assertFalse(clientIdConverter.isEncodedMemberId("XRD2:GOV:M4:SS1"));
    }

    @Test
    void isEncodedSubsystemId() {
        assertFalse(clientIdConverter.isEncodedSubsystemId("XRD2:GOV:M4"));
        assertTrue(clientIdConverter.isEncodedSubsystemId("XRD2:GOV:M4:SS1"));
    }

    @Test
    void isEncodedClientId() {
        assertTrue(clientIdConverter.isEncodedClientId("XRD2:GOV:M4"));
        assertTrue(clientIdConverter.isEncodedClientId("XRD2:GOV:M4:SS1"));
    }

    @Test
    void convertClientId() {
        ClientId clientId = ClientId.Conf.create("XRD2", "GOV", "M4", "SS1");
        String encoded = clientIdConverter.convertId(clientId);
        assertEquals("XRD2:GOV:M4:SS1", encoded);

        clientId = ClientId.Conf.create("XRD2", "GOV", "M4");
        encoded = clientIdConverter.convertId(clientId);
        assertEquals("XRD2:GOV:M4", encoded);
    }

}
