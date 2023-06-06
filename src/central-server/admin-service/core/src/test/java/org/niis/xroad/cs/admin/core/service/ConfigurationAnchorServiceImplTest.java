/*
 * The MIT License
 * <p>
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

package org.niis.xroad.cs.admin.core.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.exception.ServiceException;
import org.niis.xroad.cs.admin.api.dto.ConfigurationAnchor;
import org.niis.xroad.cs.admin.api.dto.ConfigurationAnchorWithFile;
import org.niis.xroad.cs.admin.api.dto.HAConfigStatus;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
import org.niis.xroad.cs.admin.core.entity.ConfigurationSigningKeyEntity;
import org.niis.xroad.cs.admin.core.entity.ConfigurationSourceEntity;
import org.niis.xroad.cs.admin.core.repository.ConfigurationSourceRepository;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.AuditEventHelper;
import org.springframework.util.Base64Utils;
import org.xmlunit.assertj3.XmlAssert;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.cs.admin.api.domain.ConfigurationSourceType.EXTERNAL;
import static org.niis.xroad.cs.admin.api.domain.ConfigurationSourceType.INTERNAL;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.RE_CREATE_INTERNAL_CONFIGURATION_ANCHOR;

@ExtendWith(MockitoExtension.class)
public class ConfigurationAnchorServiceImplTest {

    private static final String INTERNAL_CONFIGURATION = "internal";
    private static final String EXTERNAL_CONFIGURATION = "external";
    private static final String CENTRAL_SERVICE = "cs";
    private static final String HA_NODE_NAME = "haNodeName";
    private static final Instant FILE_UPDATED_AT = Instant.now();
    private static final String HASH = "F5:1B:1F:9C:07:23:4C:DA:E6:4C:99:CB:FC:D8:EE:0E:C5:5F:A4:AF";
    private static final byte[] FILE_DATA = "file-data".getBytes(UTF_8);

    @Mock
    private SystemParameterService systemParameterService;
    @Mock
    private ConfigurationSourceRepository configurationSourceRepository;
    @Mock
    private ConfigurationSourceEntity configurationSource;
    @Mock
    private AuditDataHelper auditDataHelper;
    @Mock
    private AuditEventHelper auditEventHelper;

    private ConfigurationAnchorServiceImpl configurationAnchorService;

    @BeforeEach
    void initConfigurationService() {
        configurationAnchorService = new ConfigurationAnchorServiceImpl(
                configurationSourceRepository,
                systemParameterService,
                auditEventHelper,
                auditDataHelper,
                new HAConfigStatus(HA_NODE_NAME, false));
    }

    @Nested
    class GetConfigurationAnchor {
        @Test
        void shouldGetInternalConfigurationAnchor() {
            when(configurationSourceRepository.findBySourceTypeAndHaNodeName(INTERNAL_CONFIGURATION, HA_NODE_NAME))
                    .thenReturn(Optional.of(configurationSourceEntity()));

            final Optional<ConfigurationAnchor> optResult = configurationAnchorService.getConfigurationAnchor(INTERNAL);

            assertThat(optResult).isPresent();

            final var result = optResult.get();
            assertThat(result.getAnchorFileHash()).isEqualTo(HASH);
            assertThat(result.getAnchorFileHash()).isEqualTo(HASH);
            assertThat(result.getAnchorGeneratedAt()).isEqualTo(FILE_UPDATED_AT);
        }

        @Test
        void shouldGetInternalConfigurationAnchorWithFile() {
            when(configurationSourceRepository.findBySourceTypeAndHaNodeName(INTERNAL_CONFIGURATION, HA_NODE_NAME))
                    .thenReturn(Optional.of(configurationSourceEntityWithFile()));

            final Optional<ConfigurationAnchorWithFile> optResult = configurationAnchorService.getConfigurationAnchorWithFile(INTERNAL);

            assertThat(optResult.isPresent()).isTrue();

            final var result = optResult.get();
            assertThat(result.getAnchorFile()).isEqualTo(FILE_DATA);
            assertThat(result.getAnchorFileHash()).isEqualTo(HASH);
            assertThat(result.getAnchorGeneratedAt()).isEqualTo(FILE_UPDATED_AT);
        }

        @Test
        void shouldGetExternalConfigurationAnchor() {
            when(configurationSourceRepository.findBySourceTypeAndHaNodeName(EXTERNAL_CONFIGURATION, HA_NODE_NAME))
                    .thenReturn(Optional.of(configurationSourceEntity()));

            final Optional<ConfigurationAnchor> optResult = configurationAnchorService.getConfigurationAnchor(EXTERNAL);
            assertThat(optResult).isPresent();

            final var result = optResult.get();
            assertThat(result.getAnchorFileHash()).isEqualTo(HASH);
            assertThat(result.getAnchorGeneratedAt()).isEqualTo(FILE_UPDATED_AT);
        }

        @Test
        void shouldGetExternalConfigurationAnchorWithFile() {
            when(configurationSourceRepository.findBySourceTypeAndHaNodeName(EXTERNAL_CONFIGURATION, HA_NODE_NAME))
                    .thenReturn(Optional.of(configurationSourceEntityWithFile()));

            final Optional<ConfigurationAnchorWithFile> optResult = configurationAnchorService.getConfigurationAnchorWithFile(EXTERNAL);
            assertThat(optResult.isPresent()).isTrue();

            final var result = optResult.get();
            assertThat(result.getAnchorFile()).isEqualTo(FILE_DATA);
            assertThat(result.getAnchorFileHash()).isEqualTo(HASH);
            assertThat(result.getAnchorGeneratedAt()).isEqualTo(FILE_UPDATED_AT);
        }

        private ConfigurationSourceEntity configurationSourceEntity() {
            return new ConfigurationSourceEntity(HASH, FILE_UPDATED_AT);
        }

        private ConfigurationSourceEntity configurationSourceEntityWithFile() {
            return new ConfigurationSourceEntity(FILE_DATA, HASH, FILE_UPDATED_AT);
        }
    }

    @Nested
    class RecreateAnchor {
        final Map<String, String> namespace = Map.of(
                "ns2", "http://x-road.eu/xsd/identifiers",
                "ns3", "http://x-road.eu/xsd/xroad.xsd"
        );
        static final String INSTANCE_IDENTIFIER = "inId";
        static final String CERT1 = "cert1";
        static final String CERT2 = "cert2";
        static final String CERT3 = "cert3";
        static final String HA_NODE_NAME2 = "haNodeName2";
        static final String CENTRAL_SERVICE2 = "cs2";
        @Mock
        private ConfigurationSigningKeyEntity signingKeyEntity1, signingKeyEntity2, signingKeyEntity3;
        @Mock
        private ConfigurationSourceEntity configurationSource2;
        @Captor
        private ArgumentCaptor<byte[]> xmlCaptor;

        @Test
        void shouldSuccessfullyRecreate() {
            when(systemParameterService.getInstanceIdentifier()).thenReturn(INSTANCE_IDENTIFIER);
            when(systemParameterService.getCentralServerAddress(HA_NODE_NAME)).thenReturn(CENTRAL_SERVICE);
            when(systemParameterService.getCentralServerAddress(HA_NODE_NAME2)).thenReturn(CENTRAL_SERVICE2);
            when(configurationSourceRepository.findBySourceTypeOrCreate(INTERNAL_CONFIGURATION.toLowerCase(),
                    new HAConfigStatus(HA_NODE_NAME, false)))
                    .thenReturn(configurationSource);
            when(configurationSourceRepository.findAllBySourceType(INTERNAL_CONFIGURATION.toLowerCase()))
                    .thenReturn(List.of(configurationSource, configurationSource2));
            when(configurationSource.getHaNodeName()).thenReturn(HA_NODE_NAME);
            when(configurationSource.getConfigurationSigningKeys())
                    .thenReturn(new LinkedHashSet<>(List.of(signingKeyEntity1, signingKeyEntity2)));
            when(configurationSource.getConfigurationSigningKey()).thenReturn(signingKeyEntity1);
            when(configurationSource2.getHaNodeName()).thenReturn(HA_NODE_NAME2);
            when(configurationSource2.getConfigurationSigningKeys()).thenReturn(Set.of(signingKeyEntity3));
            when(configurationSource2.getConfigurationSigningKey()).thenReturn(null);
            when(signingKeyEntity1.getCert()).thenReturn(CERT1.getBytes(UTF_8));
            when(signingKeyEntity2.getCert()).thenReturn(CERT2.getBytes(UTF_8));
            when(signingKeyEntity3.getCert()).thenReturn(CERT3.getBytes(UTF_8));

            final var result = configurationAnchorService.recreateAnchor(INTERNAL, true);

            verify(configurationSourceRepository).save(configurationSource);
            verify(configurationSourceRepository, never()).save(configurationSource2);
            verify(configurationSource).setAnchorFile(xmlCaptor.capture());
            verify(configurationSource).setAnchorFileHash(result.getAnchorFileHash());
            verify(configurationSource).setAnchorGeneratedAt(result.getAnchorGeneratedAt());
            verify(auditEventHelper).changeRequestScopedEvent(RE_CREATE_INTERNAL_CONFIGURATION_ANCHOR);
            verify(auditDataHelper).putAnchorHash(any());

            assertThat(result.getAnchorGeneratedAt().truncatedTo(MINUTES))
                    .isEqualTo(Instant.now().truncatedTo(MINUTES));

            final var xml = new String(xmlCaptor.getValue());
            XmlAssert.assertThat(xml).withNamespaceContext(namespace)
                    .valueByXPath("//ns3:configurationAnchor/instanceIdentifier").isEqualTo(INSTANCE_IDENTIFIER);
            XmlAssert.assertThat(xml).withNamespaceContext(namespace)
                    .valueByXPath("//ns3:configurationAnchor/generatedAt").isEqualTo(asString(result.getAnchorGeneratedAt()));
            XmlAssert.assertThat(xml).withNamespaceContext(namespace)
                    .valueByXPath("//ns3:configurationAnchor/source[1]/downloadURL")
                    .isEqualTo("http://cs/internalconf");
            XmlAssert.assertThat(xml).withNamespaceContext(namespace)
                    .valueByXPath("//ns3:configurationAnchor/source[1]/verificationCert[1]")
                    .isEqualTo(Base64Utils.encodeToString(CERT1.getBytes(UTF_8)));
            XmlAssert.assertThat(xml).withNamespaceContext(namespace)
                    .valueByXPath("//ns3:configurationAnchor/source[1]/verificationCert[2]")
                    .isEqualTo(Base64Utils.encodeToString(CERT2.getBytes(UTF_8)));

            XmlAssert.assertThat(xml).withNamespaceContext(namespace)
                    .valueByXPath("//ns3:configurationAnchor/source[2]/downloadURL")
                    .isEqualTo("http://cs2/internalconf");
            XmlAssert.assertThat(xml).withNamespaceContext(namespace)
                    .valueByXPath("//ns3:configurationAnchor/source[2]/verificationCert[1]")
                    .isEqualTo(Base64Utils.encodeToString(CERT3.getBytes(UTF_8)));
        }

        @Test
        void shouldFailIfInstanceIdentifierNotSet() {
            when(systemParameterService.getInstanceIdentifier()).thenReturn(null);

            assertThatThrownBy(() -> configurationAnchorService.recreateAnchor(INTERNAL, true))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("System parameter for instance identifier not set");
        }

        @Test
        void shouldFailIfCfgSourceDoesntHaveSigningKeys() {
            when(systemParameterService.getInstanceIdentifier()).thenReturn(INSTANCE_IDENTIFIER);
            when(configurationSourceRepository.findBySourceTypeOrCreate(INTERNAL_CONFIGURATION.toLowerCase(),
                    new HAConfigStatus(HA_NODE_NAME, false)))
                    .thenReturn(configurationSource);
            when(configurationSource.getConfigurationSigningKeys()).thenReturn(Set.of());

            assertThatThrownBy(() -> configurationAnchorService.recreateAnchor(INTERNAL, true))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("No configuration signing keys configured");
        }

        private String asString(final Instant instant) {
            return instant.truncatedTo(ChronoUnit.MILLIS).toString();
        }
    }

}
