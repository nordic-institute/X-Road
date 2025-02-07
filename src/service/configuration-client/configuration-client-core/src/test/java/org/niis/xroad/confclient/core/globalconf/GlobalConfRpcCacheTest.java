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
package org.niis.xroad.confclient.core.globalconf;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.globalconf.model.GlobalConfInitState;
import org.niis.xroad.globalconf.util.FSGlobalConfValidator;

import static ee.ria.xroad.common.SystemProperties.CONFIGURATION_PATH;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Slf4j
@ExtendWith(MockitoExtension.class)
class GlobalConfRpcCacheTest {
    private static final String GOOD_CONF_DIR = "../../../lib/globalconf-core/src/test/resources/globalconf_good_v4";

    @Spy
    FSGlobalConfValidator globalConfValidator = new FSGlobalConfValidator();
    @Spy
    GetGlobalConfRespFactory getGlobalConfRespFactory = new GetGlobalConfRespFactory();

    @InjectMocks
    GlobalConfRpcCache globalConfRpcCache;

    @Test
    void shouldLoadValidData() {
        System.setProperty(CONFIGURATION_PATH, GOOD_CONF_DIR);

        globalConfRpcCache.refreshCache();
        var result = globalConfRpcCache.getGlobalConf().globalConf();

        verify(globalConfValidator, times(1)).getReadinessState(anyString());

        assertThat(result).isPresent();
        result.ifPresent(globalConfResp -> {
            assertThat(globalConfResp.getInstanceIdentifier()).isEqualTo("EE");
            assertThat(globalConfResp.getInstancesCount()).isEqualTo(4);
        });
    }

    @Test
    void shouldNotFailOnException() {
        when(globalConfValidator.getReadinessState(anyString())).thenThrow(new IllegalStateException("random exception"));
        globalConfRpcCache.refreshCache();

        verify(globalConfValidator, times(1)).getReadinessState(anyString());
    }

    @Test
    void shouldNotLoadOnFailedValidation() {
        when(globalConfValidator.getReadinessState(anyString())).thenReturn(GlobalConfInitState.FAILURE_MALFORMED);

        globalConfRpcCache.refreshCache();
        var result = globalConfRpcCache.getGlobalConf().globalConf();

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyResponse() {

        var result = globalConfRpcCache.getGlobalConf().globalConf();

        assertThat(result).isNotPresent();
    }

}

