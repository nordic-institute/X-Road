/*
 * The MIT License
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

import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.common.exception.ValidationFailureException;
import org.niis.xroad.cs.admin.api.domain.GlobalGroup;
import org.niis.xroad.cs.admin.api.dto.GlobalGroupUpdateDto;
import org.niis.xroad.cs.admin.core.entity.GlobalGroupEntity;
import org.niis.xroad.cs.admin.core.entity.SystemParameterEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.GlobalGroupMapper;
import org.niis.xroad.cs.admin.core.entity.mapper.GlobalGroupMapperImpl;
import org.niis.xroad.cs.admin.core.entity.mapper.GlobalGroupMemberMapper;
import org.niis.xroad.cs.admin.core.entity.mapper.GlobalGroupMemberMapperImpl;
import org.niis.xroad.cs.admin.core.repository.GlobalGroupRepository;
import org.niis.xroad.cs.admin.core.repository.SystemParameterRepository;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.niis.xroad.cs.admin.core.service.SystemParameterServiceImpl.DEFAULT_SECURITY_SERVER_OWNERS_GROUP;
import static org.niis.xroad.cs.admin.core.service.SystemParameterServiceImpl.SECURITY_SERVER_OWNERS_GROUP;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.CODE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.DESCRIPTION;

@ExtendWith(MockitoExtension.class)
class GlobalGroupServiceImplTest {

    @Mock
    private AuditDataHelper auditDataHelper;
    @Mock
    private GlobalGroupRepository globalGroupRepository;
    @Mock
    private SystemParameterRepository systemParameterRepository;

    @Spy
    private GlobalGroupMapper globalGroupMapper = new GlobalGroupMapperImpl();
    @Spy
    private GlobalGroupMemberMapper globalGroupMemberMapper = new GlobalGroupMemberMapperImpl();
    @InjectMocks
    private GlobalGroupServiceImpl service;

    @Test
    void addGlobalGroup() {
        var newGlobalGroup = new GlobalGroup();

        newGlobalGroup.setGroupCode("code");
        newGlobalGroup.setDescription("description");
        when(globalGroupRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        GlobalGroup result = service.addGlobalGroup(newGlobalGroup);
        assertThat(result.getGroupCode()).isEqualTo("code");
        assertThat(result.getDescription()).isEqualTo("description");

        AssertionsForClassTypes.assertThat(result).isNotNull();
        InOrder inOrder = inOrder(globalGroupRepository, auditDataHelper);
        inOrder.verify(globalGroupRepository).getByGroupCode("code");

        inOrder.verify(globalGroupRepository).save(any());
        inOrder.verify(auditDataHelper).put(CODE, "code");
        inOrder.verify(auditDataHelper).put(DESCRIPTION, "description");

        verifyNoMoreInteractions(globalGroupRepository, auditDataHelper);
    }

    @Test
    void findGlobalGroups() {
        GlobalGroupEntity entity = new GlobalGroupEntity("code");

        when(globalGroupRepository.findAll()).thenReturn(List.of(entity));

        List<GlobalGroup> globalGroups = service.findGlobalGroups();

        assertThat(1).isEqualTo(globalGroups.size());
        AssertionsForClassTypes.assertThat(entity.getGroupCode()).isEqualTo(globalGroups.iterator().next().getGroupCode());

        verify(globalGroupRepository).findAll();
        verifyNoMoreInteractions(globalGroupRepository);
    }

    @Test
    void getGlobalGroupResultsInException() {
        assertThrows(NotFoundException.class, () -> service.getGlobalGroup("code"));
    }

    @Test
    void updateGlobalGroupDescriptionResultsInException() {
        GlobalGroupUpdateDto updateDto = new GlobalGroupUpdateDto("code", "New description");
        assertThrows(NotFoundException.class, () -> service.updateGlobalGroupDescription(updateDto));
    }

    @Test
    void deleteGlobalGroupResultsInException() {
        GlobalGroupEntity entity = new GlobalGroupEntity();
        entity.setGroupCode(DEFAULT_SECURITY_SERVER_OWNERS_GROUP);
        when(globalGroupRepository.getByGroupCode("code")).thenReturn(Optional.of(entity));
        SystemParameterEntity systemParameter = new SystemParameterEntity();
        systemParameter.setValue(DEFAULT_SECURITY_SERVER_OWNERS_GROUP);
        when(systemParameterRepository.findByKey(SECURITY_SERVER_OWNERS_GROUP)).thenReturn(List.of(systemParameter));

        assertThrows(ValidationFailureException.class, () -> service.deleteGlobalGroupMember("code"));
    }
}
