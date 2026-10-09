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
package org.niis.xroad.globalconf.impl;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.util.TimeUtils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.confclient.proto.GetGlobalConfResp;
import org.niis.xroad.confclient.proto.GetGlobalConfRespStatus;
import org.niis.xroad.confclient.proto.GetGlobalConfRespWrapped;
import org.niis.xroad.confclient.rpc.ConfClientRpcClient;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.extension.GlobalConfExtensions;
import org.niis.xroad.globalconf.impl.extension.GlobalConfExtensionFactoryImpl;
import org.niis.xroad.globalconf.model.PrivateParametersProvider;
import org.niis.xroad.globalconf.model.ServerSystemValues;
import org.niis.xroad.globalconf.model.SharedParameters;
import org.niis.xroad.globalconf.model.SharedParametersProvider;
import org.niis.xroad.globalconf.model.SharedParametersV7;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RemoteGlobalConfSourceTest {

    private static final String MAIN = "DEV";
    private static final String FOREIGN = "FOR";
    private static final String EXPIRED_FOREIGN = "OLD";
    private static final String UNKNOWN = "XXX";

    private static final ClientId FOREIGN_MEMBER = ClientId.Conf.create(FOREIGN, "COM", "333");
    private static final SecurityServerId FOREIGN_SERVER = SecurityServerId.Conf.create(FOREIGN_MEMBER, "fss0");
    private static final String FOREIGN_SYSTEM_DID = "did:web:fss0.example.org%3A7183:v1:system";
    private static final String FOREIGN_DSP_BASE_URL = "https://fss0.example.org:8183/api/dsp";
    private static final String FOREIGN_MEMBER_DID = "did:web:fss0.example.org%3A7183:v1:FOR:COM:333";

    private final Map<String, SharedParametersProvider> shared = new HashMap<>();
    private RemoteGlobalConfSource source;

    @BeforeEach
    void setUp() {
        shared.put(MAIN, provider(MAIN, "222", "ss0", null, null, null));
        shared.put(FOREIGN, provider(FOREIGN, "333", "fss0", FOREIGN_SYSTEM_DID, FOREIGN_DSP_BASE_URL, FOREIGN_MEMBER_DID));
        shared.put(EXPIRED_FOREIGN, provider(EXPIRED_FOREIGN, "444", "oss0", null, null, null)
                .refresh(TimeUtils.offsetDateTimeNow().minusDays(1)));

        var client = mock(ConfClientRpcClient.class);
        when(client.getGlobalConf()).thenReturn(GetGlobalConfRespWrapped.newBuilder()
                .setStatus(GetGlobalConfRespStatus.GLOBAL_CONF_STATUS_OK)
                .setData(GetGlobalConfResp.newBuilder().setDateRefreshed(1L).build())
                .build());
        source = new RemoteGlobalConfSource(client, new RemoteGlobalConfDataLoader() {
            @Override
            RemoteGlobalConfSource.GlobalConfData load(GetGlobalConfResp response,
                                                       Map<String, PrivateParametersProvider> basePrivateParams,
                                                       Map<String, SharedParametersProvider> baseSharedParams) {
                return new RemoteGlobalConfSource.GlobalConfData(response.getDateRefreshed(), MAIN, 7, Map.of(),
                        Map.copyOf(shared), Map.of(), new ConcurrentHashMap<>());
            }
        });
    }

    @Test
    void shouldFindSharedParametersOfEachRequestedInstance() {
        assertThat(source.findShared(MAIN)).map(SharedParameters::getInstanceIdentifier).contains(MAIN);
        assertThat(source.findShared(FOREIGN)).map(SharedParameters::getInstanceIdentifier).contains(FOREIGN);
    }

    @Test
    void shouldFindSharedParametersOfExpiredMainInstance() {
        shared.put(MAIN, shared.get(MAIN).refresh(TimeUtils.offsetDateTimeNow().minusDays(1)));

        assertThat(source.findShared(MAIN)).map(SharedParameters::getInstanceIdentifier).contains(MAIN);
    }

    @Test
    void shouldNotFindSharedParametersOfExpiredForeignInstance() {
        assertThat(source.findShared(EXPIRED_FOREIGN)).isEmpty();
    }

    @Test
    void shouldNotFindSharedParametersOfUnknownInstance() {
        assertThat(source.findShared(UNKNOWN)).isEmpty();
    }

    @Test
    void shouldFindSharedParametersCacheOfEachRequestedInstance() {
        var mainCache = source.findSharedParametersCache(MAIN);
        var foreignCache = source.findSharedParametersCache(FOREIGN);

        assertThat(mainCache).isPresent();
        assertThat(foreignCache).isPresent();
        assertThat(foreignCache.orElseThrow().getSystemValuesByServerId())
                .containsEntry(FOREIGN_SERVER, new ServerSystemValues(FOREIGN_SYSTEM_DID, FOREIGN_DSP_BASE_URL));
        assertThat(mainCache.orElseThrow().getSystemValuesByServerId()).doesNotContainKey(FOREIGN_SERVER);
    }

    @Test
    void shouldFindSharedParametersCacheOfExpiredMainInstanceButNotOfExpiredForeignInstance() {
        shared.put(MAIN, shared.get(MAIN).refresh(TimeUtils.offsetDateTimeNow().minusDays(1)));

        assertThat(source.findSharedParametersCache(MAIN)).isPresent();
        assertThat(source.findSharedParametersCache(EXPIRED_FOREIGN)).isEmpty();
        assertThat(source.findSharedParametersCache(UNKNOWN)).isEmpty();
    }

    @Test
    void shouldResolveForeignServerValuesThroughGlobalConf() {
        GlobalConfProvider globalConf = new GlobalConfImpl(source,
                new GlobalConfExtensions(source, new GlobalConfExtensionFactoryImpl()));

        assertThat(globalConf.getServerSystemValues(FOREIGN_SERVER))
                .contains(new ServerSystemValues(FOREIGN_SYSTEM_DID, FOREIGN_DSP_BASE_URL));
        assertThat(globalConf.getMemberDid(FOREIGN_MEMBER, FOREIGN_SERVER)).contains(FOREIGN_MEMBER_DID);
        assertThat(globalConf.getSecurityServerAddress(FOREIGN_SERVER)).isEqualTo("fss0.example.org");
    }

    private static SharedParametersProvider provider(String instance, String memberCode, String serverCode,
                                                     String systemDid, String dspBaseUrl, String memberDid) {
        return new SharedParametersV7(document(instance, memberCode, serverCode, systemDid, dspBaseUrl, memberDid)
                .getBytes(UTF_8));
    }

    private static String document(String instance, String memberCode, String serverCode, String systemDid,
                                   String dspBaseUrl, String memberDid) {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <ns3:conf xmlns:ns3="http://x-road.eu/xsd/xroad.xsd">
                    <instanceIdentifier>%s</instanceIdentifier>
                    <source>
                        <address>cs0</address>
                        <internalVerificationCert>dGVzdA==</internalVerificationCert>
                        <externalVerificationCert>dGVzdA==</externalVerificationCert>
                    </source>
                    <member id="m0">
                        <memberClass>
                            <code>COM</code>
                            <description>1</description>
                        </memberClass>
                        <memberCode>%s</memberCode>
                        <name>Member</name>
                        %s
                    </member>
                    <securityServer id="s0">
                        <owner>m0</owner>
                        <serverCode>%s</serverCode>
                        <address>%s.example.org</address>
                        <authCertHash>xcuWTrx4uFeP5mNBXIhYITy+gUM72CJfL63MsWXffXM=</authCertHash>
                        %s
                        %s
                    </securityServer>
                    <globalSettings>
                        <memberClass>
                            <code>COM</code>
                            <description>Companies</description>
                        </memberClass>
                        <ocspFreshnessSeconds>42</ocspFreshnessSeconds>
                    </globalSettings>
                </ns3:conf>
                """.formatted(instance, memberCode,
                memberDid == null ? "" : "<did securityServer=\"s0\">" + memberDid + "</did>",
                serverCode, serverCode,
                dspBaseUrl == null ? "" : "<dspBaseUrl>" + dspBaseUrl + "</dspBaseUrl>",
                systemDid == null ? "" : "<systemDid>" + systemDid + "</systemDid>");
    }
}
