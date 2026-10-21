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
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.keys.ConfClientConfigKeys;
import org.niis.xroad.confclient.core.config.ConfigurationClientProperties;
import org.niis.xroad.confclient.proto.GetGlobalConfRespStatus;
import org.niis.xroad.globalconf.model.GlobalConfInitState;
import org.niis.xroad.globalconf.util.FSGlobalConfValidator;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.globalconf.model.ConfigurationConstants.FILE_NAME_PRIVATE_PARAMETERS;
import static org.niis.xroad.globalconf.model.ConfigurationConstants.FILE_NAME_SHARED_PARAMETERS;

@Slf4j
@ExtendWith(MockitoExtension.class)
class GlobalConfRpcCacheTest {
    private static final String GOOD_CONF_DIR = "../../../lib/globalconf-core/src/test/resources/globalconf_good_v4";
    private static final String EXPIRED_CONF_DIR = "src/test/resources/globalconf_expired";
    private static final String NO_EXPIRY_CONF_DIR = "../../../lib/globalconf-core/src/test/resources/globalconf_good2_v3";
    private static final Instant EXPIRED_COPY_EXPIRES_ON = Instant.parse("2000-01-01T00:00:00Z");

    @Spy
    FSGlobalConfValidator globalConfValidator = new FSGlobalConfValidator();
    @Spy
    ConfigurationClientProperties configurationClientProperties = new ConfigurationClientProperties(XRoadConfigBuilder.create()
            .register(ConfClientConfigKeys.instance())
            .overrides(Map.of("xroad.configuration-client.global-conf-dir", GOOD_CONF_DIR))
            .build());
    @Spy
    GetGlobalConfRespFactory getGlobalConfRespFactory = new GetGlobalConfRespFactory(configurationClientProperties);

    @InjectMocks
    GlobalConfRpcCache globalConfRpcCache;

    @Test
    void shouldLoadValidData() {
        globalConfRpcCache.refreshCache();
        var result = globalConfRpcCache.getGlobalConf().globalConf();

        verify(globalConfValidator).getReadinessState(anyString());

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

        verify(globalConfValidator).getReadinessState(anyString());
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

    @Test
    void shouldServeExpiredCopyOnDiskWithItsExpiry() {
        var expiredCache = expiredCache();

        expiredCache.refreshCache();

        var cached = expiredCache.getGlobalConf();
        assertThat(cached.status()).isEqualTo(GetGlobalConfRespStatus.GLOBAL_CONF_STATUS_OK);
        assertThat(cached.globalConf()).isPresent();
        assertThat(cached.globalConf().get().getInstances(0).getFilesList())
                .filteredOn(f -> Set.of(FILE_NAME_PRIVATE_PARAMETERS, FILE_NAME_SHARED_PARAMETERS).contains(f.getName()))
                .hasSize(2)
                .allSatisfy(f -> {
                    assertThat(f.hasExpiresOn()).isTrue();
                    assertThat(f.getExpiresOn()).isEqualTo(EXPIRED_COPY_EXPIRES_ON.toEpochMilli());
                });
    }

    @Test
    void shouldServeCopyWithoutExpiryOnDiskWithoutExpiry() {
        var noExpiryCache = cacheFor(NO_EXPIRY_CONF_DIR);

        noExpiryCache.refreshCache();

        var cached = noExpiryCache.getGlobalConf();
        assertThat(cached.status()).isEqualTo(GetGlobalConfRespStatus.GLOBAL_CONF_STATUS_OK);
        assertThat(cached.globalConf()).isPresent();
        assertThat(cached.globalConf().get().getInstances(0).getFilesList())
                .filteredOn(f -> FILE_NAME_SHARED_PARAMETERS.equals(f.getName()))
                .hasSize(1)
                .allSatisfy(f -> assertThat(f.hasExpiresOn()).isFalse());
    }

    private static GlobalConfRpcCache expiredCache() {
        return cacheFor(EXPIRED_CONF_DIR);
    }

    private static GlobalConfRpcCache cacheFor(String confDir) {
        var properties = new ConfigurationClientProperties(XRoadConfigBuilder.create()
                .register(ConfClientConfigKeys.instance())
                .overrides(Map.of("xroad.configuration-client.global-conf-dir", confDir))
                .build());
        return new GlobalConfRpcCache(new FSGlobalConfValidator(), new GetGlobalConfRespFactory(properties), properties);
    }

}
