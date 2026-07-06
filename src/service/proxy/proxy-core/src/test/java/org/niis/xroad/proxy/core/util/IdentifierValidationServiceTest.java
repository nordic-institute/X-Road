/*
 * The MIT License
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
package org.niis.xroad.proxy.core.util;

import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.identifier.XRoadId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentifierValidationServiceTest {

    @Mock
    private ProxyProperties proxyProperties;

    @InjectMocks
    private IdentifierValidationService identifierValidationService;

    @Test
    void testCheckIdentifier() {
        var id = ServiceId.Conf.create("TEST", "CLASS", "CODE", null, "SERVICE");
        identifierValidationService.checkIdentifier(id);
    }

    @ParameterizedTest
    @MethodSource("invalidIdentifiers")
    void testCheckIdentifierLogsWarnWhenStrictCheckDisabled(XRoadId id) {
        when(proxyProperties.strictIdentifierChecks()).thenReturn(false);
        assertDoesNotThrow(() -> identifierValidationService.checkIdentifier(id));
    }

    @ParameterizedTest
    @MethodSource("invalidIdentifiers")
    void testCheckIdentifierThrowsExceptionWhenStrictCheckEnabled(XRoadId id) {
        when(proxyProperties.strictIdentifierChecks()).thenReturn(true);

        XrdRuntimeException exception = assertThrows(XrdRuntimeException.class,
                () -> identifierValidationService.checkIdentifier(id));

        assertThat(exception.getCode())
                .isEqualTo(ErrorCode.INVALID_CLIENT_IDENTIFIER.code());
        assertThat(exception.getDetails())
                .isEqualTo("Invalid character(s) in identifier " + id);
    }

    private static Stream<Arguments> invalidIdentifiers() {
        return Stream.of(
                Arguments.of(ServiceId.Conf.create("TE/ST", "CLASS", "MEMBER", "SYSTEM", "SERVICE")),
                Arguments.of(ServiceId.Conf.create("TEST", "CLASS", "MEMBER", "CO DE", "SERVICE"))
        );
    }

}
