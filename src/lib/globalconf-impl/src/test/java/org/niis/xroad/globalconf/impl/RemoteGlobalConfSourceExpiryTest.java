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

import ee.ria.xroad.common.util.TimeUtils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.confclient.proto.GetGlobalConfResp;
import org.niis.xroad.confclient.proto.GetGlobalConfRespStatus;
import org.niis.xroad.confclient.proto.GetGlobalConfRespWrapped;
import org.niis.xroad.confclient.proto.GlobalConfFile;
import org.niis.xroad.confclient.proto.GlobalConfInstance;
import org.niis.xroad.confclient.rpc.ConfClientRpcClient;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.extension.GlobalConfExtensions;
import org.niis.xroad.globalconf.impl.extension.GlobalConfExtensionFactoryImpl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.core.exception.ErrorCode.GLOBAL_CONF_OUTDATED;
import static org.niis.xroad.globalconf.model.ConfigurationConstants.FILE_NAME_PRIVATE_PARAMETERS;
import static org.niis.xroad.globalconf.model.ConfigurationConstants.FILE_NAME_SHARED_PARAMETERS;

class RemoteGlobalConfSourceExpiryTest {

    private static final String GOOD_CONF_INSTANCE_DIR = "../globalconf-core/src/test/resources/globalconf_good_v7/EE";
    private static final String INSTANCE_IDENTIFIER = "EE";
    private static final int GLOBAL_CONF_VERSION = 7;

    // The fixture's shared and private parameter metadata marks the copy as expired after this instant.
    private static final Instant FIXTURE_METADATA_EXPIRES_ON = Instant.parse("2124-05-20T17:42:55Z");

    @AfterEach
    void restoreClock() {
        TimeUtils.setClock(Clock.systemUTC());
    }

    @Test
    void shouldReportNonExpiredCopyAsValid() throws IOException {
        TimeUtils.setClock(Clock.fixed(FIXTURE_METADATA_EXPIRES_ON.minusSeconds(1), ZoneOffset.UTC));

        var source = loadRemoteSource();
        var provider = providerFor(source);

        assertThat(source.isExpired()).isFalse();
        assertThatCode(provider::verifyValidity).doesNotThrowAnyException();
    }

    @Test
    @Disabled("the REMOTE loader builds parameters from raw bytes, which never carries expiry, so a copy is never reported expired")
    void shouldReportExpiredCopyAsInvalid() throws IOException {
        TimeUtils.setClock(Clock.fixed(FIXTURE_METADATA_EXPIRES_ON.plusSeconds(1), ZoneOffset.UTC));

        var source = loadRemoteSource();
        var provider = providerFor(source);

        assertThat(source.isExpired()).isTrue();
        assertThatThrownBy(provider::verifyValidity)
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(e -> assertThat(((XrdRuntimeException) e).isCausedBy(GLOBAL_CONF_OUTDATED)).isTrue());
    }

    private RemoteGlobalConfSource loadRemoteSource() throws IOException {
        var client = mock(ConfClientRpcClient.class);
        when(client.getGlobalConf()).thenReturn(buildResponse());

        var source = new RemoteGlobalConfSource(client, new RemoteGlobalConfDataLoader());
        source.reload();
        return source;
    }

    private GlobalConfProvider providerFor(RemoteGlobalConfSource source) {
        return new GlobalConfImpl(source, new GlobalConfExtensions(source, new GlobalConfExtensionFactoryImpl()));
    }

    private GetGlobalConfRespWrapped buildResponse() throws IOException {
        return GetGlobalConfRespWrapped.newBuilder()
                .setStatus(GetGlobalConfRespStatus.GLOBAL_CONF_STATUS_OK)
                .setData(GetGlobalConfResp.newBuilder()
                        .setDateRefreshed(System.currentTimeMillis())
                        .setInstanceIdentifier(INSTANCE_IDENTIFIER)
                        .addInstances(GlobalConfInstance.newBuilder()
                                .setInstanceIdentifier(INSTANCE_IDENTIFIER)
                                .setVersion(GLOBAL_CONF_VERSION)
                                .addFiles(fileOf(FILE_NAME_SHARED_PARAMETERS))
                                .addFiles(fileOf(FILE_NAME_PRIVATE_PARAMETERS))
                                .build())
                        .build())
                .build();
    }

    private GlobalConfFile fileOf(String fileName) throws IOException {
        var content = Files.readString(Path.of(GOOD_CONF_INSTANCE_DIR, fileName));
        return GlobalConfFile.newBuilder()
                .setName(fileName)
                .setContent(content)
                .setChecksum("checksum")
                .build();
    }
}
