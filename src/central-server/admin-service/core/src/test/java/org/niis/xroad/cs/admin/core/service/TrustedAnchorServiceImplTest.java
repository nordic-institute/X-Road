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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.common.exception.ValidationFailureException;
import org.niis.xroad.cs.admin.api.domain.TrustedAnchor;
import org.niis.xroad.cs.admin.core.entity.AnchorUrlCertEntity;
import org.niis.xroad.cs.admin.core.entity.AnchorUrlEntity;
import org.niis.xroad.cs.admin.core.entity.TrustedAnchorEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.TrustedAnchorMapperImpl;
import org.niis.xroad.cs.admin.core.repository.AnchorUrlCertRepository;
import org.niis.xroad.cs.admin.core.repository.AnchorUrlRepository;
import org.niis.xroad.cs.admin.core.repository.TrustedAnchorRepository;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.service.ConfigurationVerifier;

import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;
import java.util.Set;

import static ee.ria.xroad.common.util.CryptoUtils.DEFAULT_ANCHOR_HASH_ALGORITHM_ID;
import static java.lang.ClassLoader.getSystemResource;
import static java.nio.file.Files.readAllBytes;
import static java.util.Optional.empty;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.exception.util.CommonDeviationMessage.CONF_VERIFICATION_UNREACHABLE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.ANCHOR_FILE_HASH;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.ANCHOR_FILE_HASH_ALGORITHM;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.ANCHOR_URLS;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.GENERATED_AT;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.INSTANCE_IDENTIFIER;

@ExtendWith(MockitoExtension.class)
class TrustedAnchorServiceImplTest {

    private static final String ANCHOR_HASH = "40:2A:4F:94:05:D2:9B:ED:C9:EE:A2:6D:EC:EC:11:94:5D:C9:A8:3E:29:1F:B2:92:A6:E4:DF:1D";

    @Mock
    private TrustedAnchorRepository trustedAnchorRepository;
    @Mock
    private AnchorUrlRepository anchorUrlRepository;
    @Mock
    private AnchorUrlCertRepository anchorUrlCertRepository;
    @Mock
    private ConfigurationVerifier configurationVerifier;
    @Spy
    private TrustedAnchorMapperImpl trustedAnchorMapper;
    @Mock
    private AuditDataHelper auditDataHelper;

    @InjectMocks
    private TrustedAnchorServiceImpl trustedAnchorService;

    @Nested
    class Preview {

        @Test
        void preview() throws Exception {
            final byte[] bytes = readAllBytes(Paths.get(getSystemResource("trusted-anchor/trusted-anchor.xml").toURI()));

            final TrustedAnchor preview = trustedAnchorService.preview(bytes);

            final Date anchorDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").parse("2023-02-15T09:26:34.235Z");

            assertThat(preview.getInstanceIdentifier()).isEqualTo("CS0");
            assertThat(preview.getGeneratedAt()).isEqualTo(anchorDate.toInstant());
            assertThat(preview.getTrustedAnchorHash()).isEqualTo(ANCHOR_HASH);
        }

        @Test
        void previewShouldThrowValidationFailure() {
            assertThatThrownBy(() -> trustedAnchorService.preview(new byte[0]))
                    .isInstanceOf(ValidationFailureException.class)
                    .hasMessage("Malformed anchor file");
        }
    }

    @Nested
    class Upload {

        @Test
        void uploadNew() throws Exception {
            final byte[] bytes = readAllBytes(Paths.get(getSystemResource("trusted-anchor/trusted-anchor.xml").toURI()));

            when(trustedAnchorRepository.findFirstByInstanceIdentifier("CS0")).thenReturn(empty());
            when(trustedAnchorRepository.saveAndFlush(isA(TrustedAnchorEntity.class))).thenAnswer(returnsFirstArg());

            final TrustedAnchor result = trustedAnchorService.upload(bytes);

            final Date anchorDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").parse("2023-02-15T09:26:34.235Z");

            verify(auditDataHelper).calculateAndPutAnchorHash(bytes);
            verify(auditDataHelper).put(INSTANCE_IDENTIFIER, "CS0");
            verify(auditDataHelper).putDate(GENERATED_AT, anchorDate);
            verify(auditDataHelper).put(ANCHOR_URLS, Set.of("http://cs0/internalconf"));

            verify(configurationVerifier).verifyConfiguration(any(), any());

            assertThat(result.getTrustedAnchorHash()).isEqualTo(ANCHOR_HASH);
            assertThat(result.getTrustedAnchorFile()).isEqualTo(bytes);
            assertThat(result.getGeneratedAt()).isEqualTo(anchorDate.toInstant());

            verify(anchorUrlRepository, times(1)).saveAndFlush(isA(AnchorUrlEntity.class));
            verify(anchorUrlCertRepository, times(1)).saveAndFlush(isA(AnchorUrlCertEntity.class));
        }

        @Test
        void uploadNewVerificationShouldFail() throws Exception {
            final byte[] bytes = readAllBytes(Paths.get(getSystemResource("trusted-anchor/trusted-anchor.xml").toURI()));

            doThrow(new ConfigurationVerifier.ConfigurationVerificationException(CONF_VERIFICATION_UNREACHABLE))
                    .when(configurationVerifier).verifyConfiguration(any(), any());

            assertThatThrownBy(() -> trustedAnchorService.upload(bytes))
                    .isInstanceOf(ValidationFailureException.class)
                    .hasMessage("Trusted anchor file verification failed");

            final Date anchorDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").parse("2023-02-15T09:26:34.235Z");
            verify(auditDataHelper).calculateAndPutAnchorHash(bytes);
            verify(auditDataHelper).put(INSTANCE_IDENTIFIER, "CS0");
            verify(auditDataHelper).putDate(GENERATED_AT, anchorDate);
            verify(auditDataHelper).put(ANCHOR_URLS, Set.of("http://cs0/internalconf"));

            verifyNoMoreInteractions(trustedAnchorRepository);
        }
    }

    @Nested
    class Delete {

        @Mock
        private TrustedAnchorEntity trustedAnchorEntity;

        @Test
        void delete() {
            when(trustedAnchorRepository.findFirstByTrustedAnchorHash(ANCHOR_HASH))
                    .thenReturn(Optional.of(trustedAnchorEntity));
            when(trustedAnchorEntity.getInstanceIdentifier()).thenReturn("INSTANCE");
            when(trustedAnchorEntity.getTrustedAnchorHash()).thenReturn(ANCHOR_HASH);

            trustedAnchorService.delete(ANCHOR_HASH);

            verify(auditDataHelper).put(INSTANCE_IDENTIFIER, "INSTANCE");
            verify(auditDataHelper).put(ANCHOR_FILE_HASH, ANCHOR_HASH);
            verify(auditDataHelper).put(ANCHOR_FILE_HASH_ALGORITHM, DEFAULT_ANCHOR_HASH_ALGORITHM_ID);

            verify(trustedAnchorRepository).delete(trustedAnchorEntity);
        }

        @Test
        void deleteShouldThrowNotFound() {
            when(trustedAnchorRepository.findFirstByTrustedAnchorHash(ANCHOR_HASH))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> trustedAnchorService.delete(ANCHOR_HASH))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Trusted anchor not found");

            verifyNoInteractions(auditDataHelper);
            verifyNoMoreInteractions(trustedAnchorRepository);
        }

    }
}
