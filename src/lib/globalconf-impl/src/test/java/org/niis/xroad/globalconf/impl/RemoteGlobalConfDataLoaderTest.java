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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.confclient.proto.GetGlobalConfResp;
import org.niis.xroad.confclient.proto.GlobalConfFile;
import org.niis.xroad.confclient.proto.GlobalConfInstance;
import org.niis.xroad.globalconf.model.ParametersProviderFactory;
import org.niis.xroad.globalconf.model.PrivateParametersProvider;
import org.niis.xroad.globalconf.model.SharedParametersProvider;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.globalconf.model.ConfigurationConstants.FILE_NAME_PRIVATE_PARAMETERS;
import static org.niis.xroad.globalconf.model.ConfigurationConstants.FILE_NAME_SHARED_PARAMETERS;

@ExtendWith(MockitoExtension.class)
class RemoteGlobalConfDataLoaderTest {
    private static final String INSTANCE_IDENTIFIER = "DEV";
    private static final int GLOBAL_CONF_VERSION = 7;
    private static final OffsetDateTime EXPIRES_ON = OffsetDateTime.parse("2124-05-20T17:42:55Z");

    @Mock
    ParametersProviderFactory parametersProviderFactory;
    @Mock
    PrivateParametersProvider parsedPrivateParameters;
    @Mock
    PrivateParametersProvider refreshedPrivateParameters;
    @Mock
    SharedParametersProvider parsedSharedParameters;
    @Mock
    SharedParametersProvider refreshedSharedParameters;

    @Test
    void shouldApplyCarriedExpiryToPrivateParametersOnly() {
        when(parsedPrivateParameters.refresh(EXPIRES_ON)).thenReturn(refreshedPrivateParameters);

        var data = load(Set.of(FILE_NAME_PRIVATE_PARAMETERS));

        assertThat(data.privateParameters().get(INSTANCE_IDENTIFIER)).isSameAs(refreshedPrivateParameters);
        assertThat(data.sharedParameters().get(INSTANCE_IDENTIFIER)).isSameAs(parsedSharedParameters);
        verify(parsedSharedParameters, never()).refresh(any());
    }

    @Test
    void shouldApplyCarriedExpiryToSharedParametersOnly() {
        when(parsedSharedParameters.refresh(EXPIRES_ON)).thenReturn(refreshedSharedParameters);

        var data = load(Set.of(FILE_NAME_SHARED_PARAMETERS));

        assertThat(data.privateParameters().get(INSTANCE_IDENTIFIER)).isSameAs(parsedPrivateParameters);
        assertThat(data.sharedParameters().get(INSTANCE_IDENTIFIER)).isSameAs(refreshedSharedParameters);
        verify(parsedPrivateParameters, never()).refresh(any());
    }

    @Test
    void shouldKeepParsedParametersWhenNoExpiryCarried() {
        var data = load(Set.of());

        assertThat(data.privateParameters().get(INSTANCE_IDENTIFIER)).isSameAs(parsedPrivateParameters);
        assertThat(data.sharedParameters().get(INSTANCE_IDENTIFIER)).isSameAs(parsedSharedParameters);
        verify(parsedPrivateParameters, never()).refresh(any());
        verify(parsedSharedParameters, never()).refresh(any());
    }

    private RemoteGlobalConfSource.GlobalConfData load(Set<String> filesCarryingExpiry) {
        when(parametersProviderFactory.privateParametersProvider(any())).thenReturn(parsedPrivateParameters);
        when(parametersProviderFactory.sharedParametersProvider(any())).thenReturn(parsedSharedParameters);

        try (MockedStatic<ParametersProviderFactory> factory = mockStatic(ParametersProviderFactory.class)) {
            factory.when(() -> ParametersProviderFactory.forGlobalConfVersion(GLOBAL_CONF_VERSION)).thenReturn(parametersProviderFactory);
            return new RemoteGlobalConfDataLoader().load(responseOf(filesCarryingExpiry), Map.of(), Map.of());
        }
    }

    private static GetGlobalConfResp responseOf(Set<String> filesCarryingExpiry) {
        return GetGlobalConfResp.newBuilder()
                .setInstanceIdentifier(INSTANCE_IDENTIFIER)
                .addInstances(GlobalConfInstance.newBuilder()
                        .setInstanceIdentifier(INSTANCE_IDENTIFIER)
                        .setVersion(GLOBAL_CONF_VERSION)
                        .addFiles(fileOf(FILE_NAME_PRIVATE_PARAMETERS, filesCarryingExpiry))
                        .addFiles(fileOf(FILE_NAME_SHARED_PARAMETERS, filesCarryingExpiry)))
                .build();
    }

    private static GlobalConfFile fileOf(String fileName, Set<String> filesCarryingExpiry) {
        var file = GlobalConfFile.newBuilder()
                .setName(fileName)
                .setContent(fileName)
                .setChecksum("checksum");
        if (filesCarryingExpiry.contains(fileName)) {
            file.setExpiresOn(EXPIRES_ON.toInstant().toEpochMilli());
        }
        return file.build();
    }
}
