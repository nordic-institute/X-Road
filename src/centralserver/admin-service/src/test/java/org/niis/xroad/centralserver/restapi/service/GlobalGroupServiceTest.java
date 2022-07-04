/**
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
package org.niis.xroad.centralserver.restapi.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.centralserver.openapi.model.GlobalGroupCodeAndDescription;
import org.niis.xroad.centralserver.openapi.model.GlobalGroupResource;
import org.niis.xroad.centralserver.restapi.converter.GlobalGroupConverter;
import org.niis.xroad.centralserver.restapi.dto.GlobalGroupUpdateDto;
import org.niis.xroad.centralserver.restapi.entity.GlobalGroup;
import org.niis.xroad.centralserver.restapi.entity.SystemParameter;
import org.niis.xroad.centralserver.restapi.repository.GlobalGroupRepository;
import org.niis.xroad.centralserver.restapi.repository.SystemParameterRepository;
import org.niis.xroad.centralserver.restapi.service.exception.NotFoundException;
import org.niis.xroad.centralserver.restapi.service.exception.ValidationFailureException;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.niis.xroad.centralserver.restapi.service.SystemParameterService.DEFAULT_SECURITY_SERVER_OWNERS_GROUP;
import static org.niis.xroad.centralserver.restapi.service.SystemParameterService.SECURITY_SERVER_OWNERS_GROUP;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.CODE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.DESCRIPTION;

@ExtendWith(MockitoExtension.class)
class GlobalGroupServiceTest {

    @Mock
    private AuditDataHelper auditDataHelper;
    @Mock
    private GlobalGroupRepository globalGroupRepository;
    @Mock
    private GlobalGroupConverter converter;
    @Mock
    private SystemParameterRepository systemParameterRepository;
    @InjectMocks
    private GlobalGroupService service;




    @Test
    void addGlobalGroup() {
        var globalGroupCodeAndDescription = new GlobalGroupCodeAndDescription().code("code");
        var globalGroupEntity = new org.niis.xroad.centralserver.restapi.entity.GlobalGroup();
        when(converter.toEntity(globalGroupCodeAndDescription)).thenReturn(globalGroupEntity);
        var persistedGlobalGroup = new org.niis.xroad.centralserver.restapi.entity.GlobalGroup();
        persistedGlobalGroup.setGroupCode("code");
        persistedGlobalGroup.setDescription("description");
        when(globalGroupRepository.save(globalGroupEntity)).thenReturn(persistedGlobalGroup);
        GlobalGroupResource globalGroup = new GlobalGroupResource();
        when(converter.convert(persistedGlobalGroup)).thenReturn(globalGroup);

        GlobalGroupResource result = service.addGlobalGroup(globalGroupCodeAndDescription);

        assertNotNull(result);
        InOrder inOrder = inOrder(globalGroupRepository, converter, auditDataHelper);
        inOrder.verify(globalGroupRepository).getByGroupCode("code");
        inOrder.verify(converter).toEntity(globalGroupCodeAndDescription);
        inOrder.verify(globalGroupRepository).save(globalGroupEntity);
        inOrder.verify(auditDataHelper).put(CODE, "code");
        inOrder.verify(auditDataHelper).put(DESCRIPTION, "description");
        inOrder.verify(converter).convert(persistedGlobalGroup);
        verifyNoMoreInteractions(globalGroupRepository, converter, auditDataHelper);
    }
    @Test
    void findGlobalGroups() {
        GlobalGroup entity = new GlobalGroup();
        when(globalGroupRepository.findAll()).thenReturn(List.of(entity));
        GlobalGroupResource globalGroup = new GlobalGroupResource();
        when(converter.convert(entity)).thenReturn(globalGroup);

        Set<GlobalGroupResource> globalGroups = service.findGlobalGroups(null);

        assertEquals(1, globalGroups.size());
        assertEquals(globalGroup, globalGroups.iterator().next());

        InOrder inOrder = inOrder(globalGroupRepository, converter);
        inOrder.verify(globalGroupRepository).findAll();
        inOrder.verify(converter).convert(entity);
        verifyNoMoreInteractions(globalGroupRepository, converter);
    }

    @Test
    void findGlobalGroupsContainsMemberIsEmpty() {
        GlobalGroup entity = new GlobalGroup();
        when(globalGroupRepository.findAll()).thenReturn(List.of(entity));
        GlobalGroupResource globalGroup = new GlobalGroupResource();
        when(converter.convert(entity)).thenReturn(globalGroup);

        Set<GlobalGroupResource> globalGroups = service.findGlobalGroups("");

        assertEquals(1, globalGroups.size());
        assertEquals(globalGroup, globalGroups.iterator().next());

        InOrder inOrder = inOrder(globalGroupRepository, converter);
        inOrder.verify(globalGroupRepository).findAll();
        inOrder.verify(converter).convert(entity);
        verifyNoMoreInteractions(globalGroupRepository, converter);
    }

    @Test
    void findGlobalGroupsContainsMemberNotExistsInGlobalGroup() {
        GlobalGroup entity = new GlobalGroup();
        when(globalGroupRepository.findAll()).thenReturn(List.of(entity));

        Set<GlobalGroupResource> globalGroups = service.findGlobalGroups("CS:ORG:123");

        assertEquals(0, globalGroups.size());
    }

    @Test
    void getGlobalGroupResultsInException() {
        assertThrowsExactly(NotFoundException.class, () -> service.getGlobalGroup(1));
    }

    @Test
    void updateGlobalGroupDescriptionResultsInException() {
        GlobalGroupUpdateDto updateDto = new GlobalGroupUpdateDto(1, "New description");
        assertThrowsExactly(NotFoundException.class, () -> service.updateGlobalGroupDescription(updateDto));
    }

    @Test
    void deleteGlobalGroupResultsInException() {
        GlobalGroup entity = new GlobalGroup();
        entity.setGroupCode(DEFAULT_SECURITY_SERVER_OWNERS_GROUP);
        when(globalGroupRepository.findById(1)).thenReturn(Optional.of(entity));
        SystemParameter systemParameter = new SystemParameter();
        systemParameter.setValue(DEFAULT_SECURITY_SERVER_OWNERS_GROUP);
        when(systemParameterRepository.findByKey(SECURITY_SERVER_OWNERS_GROUP)).thenReturn(List.of(systemParameter));

        assertThrowsExactly(ValidationFailureException.class, () -> service.deleteGlobalGroup(1));
    }
}
