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
package ee.ria.xroad.common.conf.globalconf;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static ee.ria.xroad.common.conf.globalconf.GlobalConfInitState.FAILURE_CONFIGURATION_ERROR;
import static ee.ria.xroad.common.conf.globalconf.GlobalConfInitState.FAILURE_MALFORMED;
import static ee.ria.xroad.common.conf.globalconf.GlobalConfInitState.FAILURE_MISSING_INSTANCE_IDENTIFIER;
import static ee.ria.xroad.common.conf.globalconf.GlobalConfInitState.READY_TO_INIT;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FSGlobalConfValidatorTest {
    private final FSGlobalConfValidator fsGlobalConfValidator = new FSGlobalConfValidator();

    static Stream<Arguments> readinessStateProvider() {
        return Stream.of(
                Arguments.of("src/test/resources/globalconf_good_v4", READY_TO_INIT),
                Arguments.of("-", FAILURE_CONFIGURATION_ERROR),
                Arguments.of("src/test/resources/random_path", FAILURE_CONFIGURATION_ERROR),
                Arguments.of("src/test/resources/globalconf_good_v4/instance-identifier", FAILURE_CONFIGURATION_ERROR),
                Arguments.of("src/test/resources/globalconf_missing_identifier", FAILURE_MISSING_INSTANCE_IDENTIFIER),
                Arguments.of("src/test/resources/globalconf_empty", FAILURE_MALFORMED)
        );
    }

    @ParameterizedTest
    @MethodSource("readinessStateProvider")
    void testReadinessState(String configPath, GlobalConfInitState expectedState) {
        var result = fsGlobalConfValidator.getReadinessState(configPath);
        assertEquals(expectedState, result);
    }
}
