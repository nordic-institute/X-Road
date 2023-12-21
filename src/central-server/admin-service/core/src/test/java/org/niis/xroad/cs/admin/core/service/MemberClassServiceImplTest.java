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
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.exception.DataIntegrityException;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.domain.MemberClass;
import org.niis.xroad.cs.admin.core.entity.MemberClassEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.MemberClassMapperImpl;
import org.niis.xroad.cs.admin.core.repository.MemberClassRepository;
import org.niis.xroad.cs.admin.core.repository.XRoadMemberRepository;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
//@Disabled
class MemberClassServiceImplTest {

    private static final String CODE = "code";
    private static final String DESCRIPTION = "description";

    @Mock
    private XRoadMemberRepository members;
    @Mock
    private MemberClassRepository memberClassRepository;
    @Mock
    private AuditDataHelper auditData;

    @Spy
    private MemberClassMapperImpl memberClassMapper;

    @InjectMocks
    private MemberClassServiceImpl memberClassService;

    private final MemberClassEntity memberClassEntity = new MemberClassEntity(CODE, DESCRIPTION);

    @Nested
    class FindAll {

        @Captor
        private ArgumentCaptor<Sort> argumentCaptor;

        @Test
        void findAll() {
            List<MemberClassEntity> list = List.of(memberClassEntity, memberClassEntity);
            when(memberClassRepository.findAllSortedBy(isA(Sort.class)))
                    .thenReturn(list);

            final java.util.List<MemberClass> result = memberClassService.findAll();

            assertThat(result).hasSize(2);
            verify(memberClassRepository).findAllSortedBy(argumentCaptor.capture());
            assertThat(argumentCaptor.getValue().iterator().next()).satisfies(order -> {
                assertThat(order.isIgnoreCase()).isTrue();
                assertThat(order.getProperty()).isEqualTo("code");
            });
        }
    }

    @Nested
    class FindByCode {

        @Test
        void findByCode() {
            when(memberClassRepository.findByCode(CODE)).thenReturn(Optional.of(memberClassEntity));

            final Optional<MemberClass> result = memberClassService.findByCode(CODE);

            assertThat(result).isPresent();
            assertThat(result.get().getCode()).isEqualTo(CODE);
            assertThat(result.get().getDescription()).isEqualTo(DESCRIPTION);
            verify(memberClassMapper).toTarget(memberClassEntity);
        }

        @Test
        void findByCodeShouldReturnEmpty() {
            when(memberClassRepository.findByCode(CODE)).thenReturn(Optional.empty());

            final Optional<MemberClass> result = memberClassService.findByCode(CODE);

            assertThat(result).isEmpty();
            verifyNoInteractions(memberClassMapper);
        }
    }

    @Nested
    class Add {

        @Captor
        private ArgumentCaptor<MemberClassEntity> argumentCaptor;

        @Test
        void add() {
            when(memberClassRepository.findByCode(CODE)).thenReturn(Optional.empty());
            when(memberClassRepository.save(isA(MemberClassEntity.class))).thenReturn(memberClassEntity);

            MemberClass dto = new MemberClass(CODE, DESCRIPTION);
            final MemberClass result = memberClassService.add(dto);

            verify(memberClassRepository).save(argumentCaptor.capture());
            assertThat(argumentCaptor.getValue()).satisfies(memberClass -> {
                        assertThat(memberClass.getCode()).isEqualTo(CODE);
                        assertThat(memberClass.getDescription()).isEqualTo(DESCRIPTION);
                    }
            );
            assertThat(result.getCode()).isEqualTo(CODE);
            assertThat(result.getDescription()).isEqualTo(DESCRIPTION);

            verify(auditData).put(RestApiAuditProperty.CODE, CODE);
            verify(auditData).put(RestApiAuditProperty.DESCRIPTION, DESCRIPTION);
        }

        @Test
        void addShouldFailWhenCodeExists() {
            when(memberClassRepository.findByCode(CODE)).thenReturn(Optional.of(memberClassEntity));

            assertThatThrownBy(() -> memberClassService.add(new MemberClass(CODE, DESCRIPTION)))
                    .isInstanceOf(DataIntegrityException.class)
                    .hasMessage("Member class with the same code already exists.");

            verifyNoMoreInteractions(memberClassRepository);
            verify(auditData).put(RestApiAuditProperty.CODE, CODE);
            verify(auditData).put(RestApiAuditProperty.DESCRIPTION, DESCRIPTION);
        }
    }

    @Nested
    class Update {

        @Captor
        private ArgumentCaptor<MemberClassEntity> argumentCaptor;

        @Mock
        private MemberClassEntity memberClassEntityMock;

        @Test
        void updateDescription() {
            when(memberClassEntityMock.getCode()).thenReturn(CODE);
            when(memberClassEntityMock.getDescription()).thenReturn(DESCRIPTION);
            when(memberClassRepository.findByCode(CODE)).thenReturn(Optional.of(memberClassEntityMock));
            when(memberClassRepository.save(memberClassEntityMock)).thenReturn(memberClassEntityMock);

            final MemberClass result = memberClassService.updateDescription(CODE, DESCRIPTION);

            verify(memberClassEntityMock).setDescription(DESCRIPTION);

            verify(memberClassRepository).save(argumentCaptor.capture());
            assertThat(argumentCaptor.getValue()).satisfies(memberClass -> {
                        assertThat(memberClass.getCode()).isEqualTo(CODE);
                        assertThat(memberClass.getDescription()).isEqualTo(DESCRIPTION);
                    }
            );
            assertThat(result.getCode()).isEqualTo(CODE);
            assertThat(result.getDescription()).isEqualTo(DESCRIPTION);
            verify(auditData).put(RestApiAuditProperty.CODE, CODE);
            verify(auditData).put(RestApiAuditProperty.DESCRIPTION, DESCRIPTION);
        }

        @Test
        void updateShouldThrowNotFound() {
            when(memberClassRepository.findByCode(CODE)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> memberClassService.updateDescription(CODE, DESCRIPTION))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("No member class with the specified code found.");

            verifyNoMoreInteractions(memberClassRepository);
            verify(auditData).put(RestApiAuditProperty.CODE, CODE);
            verify(auditData).put(RestApiAuditProperty.DESCRIPTION, DESCRIPTION);
        }
    }

    @Nested
    class Delete {

        @Test
        void delete() {
            when(memberClassRepository.findByCode(CODE)).thenReturn(Optional.of(memberClassEntity));
            when(members.existsByMemberClass(memberClassEntity)).thenReturn(FALSE);

            memberClassService.delete(CODE);

            verify(memberClassRepository).delete(memberClassEntity);
            verify(auditData).put(RestApiAuditProperty.CODE, CODE);
        }

        @Test
        void deleteShouldThrowNotFound() {
            when(memberClassRepository.findByCode(CODE)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> memberClassService.delete(CODE))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("No member class with the specified code found.");

            verifyNoMoreInteractions(memberClassRepository);
            verify(auditData).put(RestApiAuditProperty.CODE, CODE);
        }

        @Test
        void deleteShouldFailWhenMembersExist() {
            when(memberClassRepository.findByCode(CODE)).thenReturn(Optional.of(memberClassEntity));
            when(members.existsByMemberClass(memberClassEntity)).thenReturn(TRUE);

            assertThatThrownBy(() -> memberClassService.delete(CODE))
                    .isInstanceOf(DataIntegrityException.class)
                    .hasMessage("Cannot delete member class: Found X-Road members belonging to the class."
                            + " Only classes with no registered members can be deleted.");

            verifyNoMoreInteractions(memberClassRepository);
            verify(auditData).put(RestApiAuditProperty.CODE, CODE);
        }
    }

}
